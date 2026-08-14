# Danh sách 20 Lỗi Hệ thống (Microservices)

Dưới đây là chi tiết về 10 lỗi cơ bản và 10 lỗi hiếm gặp trong kiến trúc dự án (Spring Boot, Eureka, Gateway, Redis, RabbitMQ, PostgreSQL, MongoDB).

> [!NOTE]
> **Lưu ý phân loại lỗi và cách kích hoạt:**
> 1. **Các lỗi thuộc về Cấu hình & Mã nguồn**: Đã được chèn sẵn mã giả lập hoặc cấu hình tắt (được comment lại) trực tiếp trong source code Java và file properties của dự án. Bạn chỉ cần bỏ comment các đoạn này để kích hoạt lỗi.
> 2. **Các lỗi thuộc về tính chất mặc định của hệ thống**: Là các lỗi kiến trúc đang tồn tại sẵn trong mã nguồn hiện tại của dự án (như thiếu distributed tracing) mà không cần can thiệp.
> 3. **Các lỗi thuộc về hạ tầng & lệnh quản trị mạng**: Đây là các lỗi liên quan đến runtime của Docker, sự cố vật lý hoặc kết nối mạng ngoài. Chúng không thể can thiệp bằng code Java của ứng dụng mà được ghi chú hướng dẫn giả lập từng bước thông qua các dòng lệnh CLI (Docker, RabbitMQ UI, mạng...) bên dưới từng mục tương ứng.

---

### 1. Sập Eureka Server hoặc Mất Đồng Bộ Service Registry
* **Hiện tượng**: `discovery-client-service` (Eureka) bị sập hoặc gặp sự cố kết nối mạng khiến các service không thể cập nhật hoặc gửi heartbeat.
* **Hậu quả**: API Gateway và các Feign Client (`lb://`) không thể phân giải được IP của các service khác, dẫn đến lỗi `503 Service Unavailable` hoặc lỗi Timeout hàng loạt khi gọi API chéo.
* **Cách giả lập hiện tượng trong dự án này**: Thay đổi `EUREKA_SERVER_URL` trong file `docker-compose.yml` thành `http://discovery-client-service-broken:8761/eureka`. Lúc này container Eureka Server vẫn khởi động bình thường để thỏa mãn `depends_on` của các service khác, nhưng các service nghiệp vụ và API Gateway sẽ báo lỗi kết nối `Connection Refused` do trỏ sang host không tồn tại.

### 2. Lỗi Sụp Đổ Dây Chuyền (Cascading Failure) do thiếu Circuit Breaker
* **Hiện tượng**: `account-service` hoặc `credit-card-service` gọi đồng bộ (Synchronous HTTP) sang `user-service` qua Feign Client. Khi `user-service` bị chậm hoặc nghẽn, các request bên gọi sẽ bị treo để chờ phản hồi.
* **Hậu quả**: Cạn kiệt luồng xử lý (Thread Pool Exhaustion) ở service gọi khiến nó cũng bị sập theo. Cần tích hợp cơ chế Circuit Breaker (như Resilience4j) để ngắt mạch kịp thời khi service đích gặp sự cố.
* **Cách giả lập hiện tượng trong dự án này**: Thêm `Thread.sleep(10000)` vào API `findById` trong `UserController.java` của `user-service`. Lúc này, bất kỳ yêu cầu nào liên quan đến giao dịch tài khoản hay thẻ tín dụng (cần lấy thông tin user) đều sẽ bị nghẽn luồng xử lý do phải chờ đợi phản hồi từ `user-service`.

### 3. Bất Đồng Nhất Dữ Liệu Phân Tán (Distributed Data Inconsistency)
* **Hiện tượng**: Một giao dịch nghiệp vụ nghiệp vụ trải dài qua nhiều service (ví dụ: tạo thẻ tín dụng cần gọi cả `user-service` và `bank-service`).
* **Hậu quả**: Nếu bước gọi sang `bank-service` thất bại sau khi dữ liệu ở `credit-card-service` đã được lưu, hệ thống sẽ bị sai lệch dữ liệu (không thể rollback tự động bằng `@Transactional` thông thường). Cần áp dụng **Saga Pattern** hoặc **Transactional Outbox**.
* **Cách giả lập hiện tượng trong dự án này**: Thêm dòng lệnh `throw new RuntimeException("Mô phỏng lỗi sau khi lưu DB");` ngay sau câu lệnh `this.accountRepository.save(account);` trong phương thức `save` của `AccountServiceImpl.java`. Khi gọi API tạo tài khoản, bản ghi tài khoản vẫn sẽ được lưu xuống PostgreSQL của `account-service`, nhưng client sẽ nhận về lỗi `500 Internal Server Error` và không có log/event nào được ghi nhận.

### 4. Nghẽn Hàng Đợi RabbitMQ (Queue Congestion/OOM)
* **Hiện tượng**: Các service đẩy log qua RabbitMQ (`logs.queue`) để `log-service` lưu vào MongoDB. Nếu `log-service` hoặc MongoDB bị chậm/sập, các tin nhắn log sẽ tích tụ trong queue.
* **Hậu quả**: Hàng đợi bị tràn bộ nhớ (Memory High Watermark), khiến RabbitMQ chặn (block) toàn bộ kết nối từ các service gửi log, làm nghẽn toàn bộ hệ thống nghiệp vụ chính.
* **Cách giả lập hiện tượng trong dự án này**: Tắt Listener bằng cách comment out annotation `@RabbitListener` trong class `LogConsumer.java` của `log-service`. Khi các service khác chạy giao dịch và đẩy log liên tục lên RabbitMQ, hàng đợi `logs.queue` sẽ tăng dần số lượng tin nhắn (Message count) mà không bao giờ được tiêu thụ.

### 5. Lỗi Đồng Bộ Cache Redis (Cache Inconsistency)
* **Hiện tượng**: Các service sử dụng Redis để cache thông tin người dùng/tài khoản. Dữ liệu dưới PostgreSQL thay đổi nhưng cache trong Redis chưa được xóa hoặc cập nhật do lỗi kết nối mạng tạm thời.
* **Hậu quả**: Client liên tục đọc phải dữ liệu cũ (stale data) từ cache, dẫn đến sai lệch thông tin giao dịch hoặc số dư hiển thị.
* **Cách giả lập hiện tượng trong dự án này**: Thay đổi thuộc tính `@CacheEvict(value = Caches.USERS_CACHE, allEntries = true, condition = "#result.success != false")` thành `condition = "false"` hoặc comment out `@CachePut` và `@CacheEvict` trong phương thức `update` của `UserServiceImpl.java`. Khi gọi API cập nhật thông tin User, DB PostgreSQL sẽ thay đổi nhưng Redis Cache vẫn giữ dữ liệu cũ.

### 6. Điểm Nghẽn Duy Nhất (Single Point of Failure - SPOF) tại Gateway
* **Hiện tượng**: Chỉ chạy duy nhất 1 instance của `api-gateway-service`.
* **Hậu quả**: Khi Gateway bị sập hoặc quá tải luồng định tuyến, toàn bộ các API của hệ thống từ bên ngoài gọi vào đều bị chặn hoàn toàn, dù các service nghiệp vụ bên trong vẫn hoạt động bình thường.
* **Cách giả lập hiện tượng trong dự án này**: Thực hiện tắt nóng gateway container bằng lệnh `docker-compose stop api-gateway-service`. Tất cả request gọi từ bên ngoài vào port `8087` sẽ lập tức thất bại, trong khi các microservice nội bộ bên dưới vẫn chạy bình thường.

### 7. Cạn Kiệt Connection Pool của Database (PostgreSQL)
* **Hiện tượng**: Các service kết nối chung vào cùng một container `postgres-db` (dùng chung tài nguyên máy chủ).
* **Hậu quả**: Khi một service chạy các query nặng, không tối ưu hoặc bị rò rỉ kết nối (connection leak), nó sẽ chiếm dụng hết tài nguyên của database dùng chung, làm treo kết nối và lỗi Timeout ở toàn bộ các service khác.
* **Cách giả lập hiện tượng trong dự án này**: Thêm cấu hình giới hạn kết nối `spring.datasource.hikari.maximum-pool-size=1` và timeout kết nối `spring.datasource.hikari.connection-timeout=2000` vào file `application.properties` của `user-service`. Sau đó gọi một API bất kỳ của `user-service` với một luồng bị khóa giữ kết nối lâu, các request tiếp theo đến `user-service` sẽ lập tức bị lỗi nghẽn connection pool.

### 8. Chế độ Tự Bảo Vệ của Eureka (Eureka Self-Preservation)
* **Hiện tượng**: Khi mất kết nối mạng tạm thời giữa các server, Eureka tự động kích hoạt chế độ "Self-Preservation" để tránh xóa nhầm các service instance vẫn còn sống.
* **Hậu quả**: Eureka sẽ giữ lại cả những instance đã thực sự chết. API Gateway vẫn tiếp tục điều hướng request vào các instance chết này, tạo ra lỗi kết nối chập chờn cho người dùng cuối.
* **Cách giả lập hiện tượng trong dự án này**: Cấu hình `eureka.server.enable-self-preservation=true` (mặc định) trong `discovery-client-service`. Sau đó khởi động hệ thống và đột ngột tắt (kill container) `user-service` mà không hủy đăng ký (deregister). Eureka sẽ không xóa `user-service` khỏi registry và Gateway vẫn gửi request vào container đã chết.

### 9. Thiếu Truy Vết Phân Tán (Lack of Distributed Tracing)
* **Hiện tượng**: Một lỗi xảy ra khi gọi chuỗi API qua Gateway -> Account Service -> User Service.
* **Hậu quả**: Việc debug cực kỳ khó khăn vì log nằm phân tán ở nhiều container khác nhau và không có một ID định danh chung để liên kết hành trình của request. Cần tích hợp thêm **Sleuth/Micrometer Tracing** và **Zipkin** để gán `Trace ID` đi xuyên suốt các service.
* **Cách giả lập hiện tượng trong dự án này**: Lỗi này mặc định đang tồn tại trong dự án vì các file `pom.xml` của các service hoàn toàn không tích hợp thư viện Zipkin/Sleuth hay Spring Cloud OpenTelemetry. Khi các log được in ra từ nhiều service khác nhau cho cùng một request, chúng không có `Trace ID` hay `Span ID` đi kèm để đối chiếu.

### 10. Lỗi Phiên Bản và Cấu Hình Feign Client Không Khớp
* **Hiện tượng**: Tên các service được đăng ký trên Eureka bằng chữ thường (như `bank-service`) nhưng Feign Client hoặc cấu hình Gateway map tên bằng chữ hoa hoặc ngược lại mà không đồng nhất.
* **Hậu quả**: Feign client báo lỗi `No instances available for bank-service` ngay cả khi service đang hoạt động bình thường, do cơ chế phân giải tên bị sai lệch cấu hình.
* **Cách giả lập hiện tượng trong dự án này**: Đổi giá trị `spring.application.name` trong `user-service` thành `user-service-broken`. Eureka sẽ đăng ký service với tên mới, nhưng Feign client ở `account-service` vẫn cố tìm kiếm client có tên `@FeignClient(name = "user-service")`, dẫn đến lỗi không tìm thấy service instance.

---

# Danh sách 10 Lỗi Hệ thống Hiếm gặp (Rare/Edge Cases)

### 1. Phân mảnh mạng (Split-Brain) ở Eureka Cluster
* **Hiện tượng**: Khi triển khai cụm Eureka cluster, mạng bị phân mảnh cô lập các Eureka Server. Một số microservice đăng ký với Eureka-1, số khác đăng ký với Eureka-2.
* **Hậu quả**: Hai Eureka Server không đồng bộ Registry. Gateway kết nối với Eureka-1 chỉ thấy một nửa số service và báo lỗi `503` hoặc `404` chập chờn khi gọi các service thuộc nửa bên kia.
* **Cách giả lập hiện tượng trong dự án này**: Vì dự án đang chạy 1 instance Eureka nên để giả lập, ta dựng thêm Eureka-2 trong `docker-compose.yml` rồi ngắt kết nối mạng giữa 2 Eureka container bằng lệnh `docker network disconnect <network_name> <container_name>`.

### 2. Trôi/Mất gói tin RabbitMQ âm thầm (TCP Silent Drop / Half-Open Connection)
* **Hiện tượng**: Kết nối TCP giữa một service và RabbitMQ bị thiết bị mạng ở giữa cắt đứt một chiều (do idle lâu). Vì keep-alive lâu, service vẫn tiếp tục đẩy log qua RabbitMQ mà không nhận ra gói tin bị mất ở tầng mạng.
* **Hậu quả**: Nghiệp vụ chính vẫn thành công nhưng không có log/event nào được lưu trong MongoDB của `log-service`.
* **Cách giả lập hiện tượng trong dự án này**: Chạy lệnh `docker exec -it <rabbitmq_container> rabbitmqctl close_connection <connection_id> "Mô phỏng đứt mạng"` hoặc dùng iptables để chặn luồng mạng 5672 từ một service trong lúc đang chạy giao dịch ghi log.

### 3. Starvation Connection Pool tại MongoDB
* **Hiện tượng**: Hệ thống chịu tải lớn, `log-service` nhận quá nhiều log gửi về cùng lúc. Số lượng thread xử lý ghi vào MongoDB vượt quá kích thước Connection Pool tối đa.
* **Hậu quả**: Các thread xử lý log của RabbitMQ Consumer bị treo để đợi connection sang MongoDB, gây ra nghẽn ngược (backpressure) làm treo consumer thread và làm tăng RAM của `log-service`.
* **Cách giả lập hiện tượng trong dự án này**: Trong file `application.properties` của `log-service`, thay đổi URI connection thành `mongodb://unaldi:eu1189@mongo:27017/banking-microservices?authSource=admin&maxPoolSize=1` (giới hạn pool về 1 kết nối). Sau đó, chạy công cụ bắn tải log đồng thời từ nhiều service khác nhau để kích hoạt hàng loạt luồng ghi đè tranh chấp 1 connection duy nhất.

### 4. Bám bẩn Cache Redis do Race Condition khi cập nhật (Cache Stampede)
* **Hiện tượng**: Một luồng thực hiện xóa cache trên Redis để chuẩn bị cập nhật PostgreSQL. Ngay lập tức (trước khi DB ghi xong), một request đọc gửi tới, thấy cache trống nên truy vấn DB (lấy dữ liệu cũ) và ghi đè lại vào Redis.
* **Hậu quả**: Dữ liệu trong Redis là dữ liệu cũ, dữ liệu trong DB là dữ liệu mới. Cache bị bẩn vĩnh viễn cho đến khi có lượt update tiếp theo hoặc hết hạn TTL.
* **Cách giả lập hiện tượng trong dự án này**: Thêm `Thread.sleep(1000)` ngay trước dòng `this.userRepository.save(user);` trong `update` của `UserServiceImpl.java`. Trong lúc luồng update đang ngủ 1 giây, hãy liên tục gửi request lấy thông tin User qua API `GET /api/v1/users/{id}` để request đọc ghi ngược dữ liệu cũ lại vào Redis Cache.

### 5. JVM Stop-The-World (GC Pause) làm Eureka hủy đăng ký nhầm
* **Hiện tượng**: Một service (ví dụ `user-service`) gặp hiện tượng rò rỉ bộ nhớ hoặc xử lý tác vụ quá nặng làm JVM đóng băng để dọn rác (GC Pause). Thời gian đóng băng vượt quá thời gian heartbeat lease của Eureka.
* **Hậu quả**: Eureka Server coi như instance này đã chết và xóa nó ra khỏi Registry. Khi GC xong, service hoạt động lại bình thường nhưng Gateway sẽ không gửi request tới nó nữa.
* **Cách giả lập hiện tượng trong dự án này**: Sử dụng lệnh pause container `docker pause user-service` để mô phỏng JVM bị đóng băng hoàn toàn. Đợi quá 90 giây (thời gian thuê lease mặc định của Eureka), sau đó chạy `docker unpause user-service`. Eureka Server đã hủy đăng ký của service này mặc dù tiến trình Java trong container vẫn đang hoạt động lại bình thường.

### 6. Khóa luồng RabbitMQ Consumer do "Poison Pill"
* **Hiện tượng**: Một service thay đổi cấu trúc log gửi đi nhưng chưa cập nhật class nhận ở `log-service`. Khi một tin nhắn cấu trúc mới được gửi đi, Jackson Serializer ở đầu nhận không thể giải tuần tự hóa được đối tượng JSON này.
* **Hậu quả**: RabbitMQ liên tục từ chối tin nhắn và đẩy ngược lại (re-queue) lên đầu hàng đợi. Nó tạo ra một vòng lặp vô hạn (Infinite Retry Loop) khiến CPU của `log-service` vọt lên 100% và không thể xử lý các log khác.
* **Cách giả lập hiện tượng trong dự án này**: Gửi một message sai định dạng JSON hoặc có thuộc tính không thể parse sang class `LogResponse` trực tiếp từ giao diện quản trị RabbitMQ UI (cổng 15672) vào `logs.queue`.

### 7. Deadlock tại Connection Pool HikariCP (Hikari Pool Deadlock)
* **Hiện tượng**: Một phương thức cha `@Transactional` lấy ra Connection-1 từ Pool để chạy. Trong phương thức đó lại gọi một phương thức con có cấu hình `@Transactional(propagation = Propagation.REQUIRES_NEW)` (yêu cầu tạo kết nối mới Connection-2). Dưới tải cao, tất cả connection trong pool bị chiếm giữ bởi các luồng cha (đều giữ Connection-1), khiến luồng con không thể lấy Connection-2.
* **Hậu quả**: Xảy ra hiện tượng nghẽn chéo (Deadlock) ở pool kết nối DB, ném ra lỗi Timeout từ HikariCP.
* **Cách giả lập hiện tượng trong dự án này**: Tạo phương thức con được đánh dấu `@Transactional(propagation = Propagation.REQUIRES_NEW)` bên trong `AccountServiceImpl.java` và gọi nó từ một phương thức `@Transactional` cha. Giới hạn `spring.datasource.hikari.maximum-pool-size=1` trong cấu hình database để tạo deadlock ngay tức khắc khi chạy.

### 8. Lỗi giữ IP cũ (Stale Registry IP) khi scale container
* **Hiện tượng**: Khi scale-down hoặc redeploy container, container cũ bị tắt đi, container mới được cấp IP mới. Tuy nhiên, Eureka Server hoặc client load balancer cache chưa kịp cập nhật (mất 30s-90s).
* **Hậu quả**: Gateway vẫn chuyển tiếp request đến IP cũ của container đã chết, tạo ra lỗi kết nối chập chờn cho người dùng cuối.
* **Cách giả lập hiện tượng trong dự án này**: Triển khai scale service bằng lệnh `docker-compose up --scale user-service=2 -d`. Sau đó tắt đột ngột 1 instance bằng `docker kill`. Gọi liên tục API của `user-service` qua Gateway, bạn sẽ thấy lỗi `500` hoặc kết nối không thành công xảy ra xen kẽ trong khoảng 30 giây đầu tiên.

### 9. Lỗi bất đồng bộ thứ tự xử lý log (Out-of-Order Log Events)
* **Hiện tượng**: Nhiều luồng gửi log bất đồng bộ lên RabbitMQ. Do cơ chế xử lý song song và độ trễ mạng khác nhau, hành động xảy ra sau lại được ghi nhận và lưu trữ trước hành động xảy ra trước.
* **Hậu quả**: Thứ tự log bị đảo lộn trên MongoDB, gây sai lệch thông tin phân tích lịch sử giao dịch.
* **Cách giả lập hiện tượng trong dự án này**: Gửi liên tiếp 2 hành động Tạo và Xóa tài khoản người dùng ngay lập tức trong vòng 1-2 ms. Do RabbitMQ gửi bất đồng bộ và nhiều luồng tiêu thụ ở `log-service` chạy song song, sự kiện Xóa có thể được lưu trước sự kiện Tạo.

### 10. Tràn bộ nhớ Gateway do rò rỉ RAM Netty
* **Hiện tượng**: Spring Cloud Gateway sử dụng Netty để xử lý I/O bất đồng bộ. Khi có lượng request tải rất lớn truyền kèm theo các header hoặc body lớn, nếu bộ nhớ trực tiếp (Direct Memory) không được giải phóng kịp thời sẽ gây rò rỉ bộ nhớ.
* **Hậu quả**: Bộ nhớ RAM vật lý của Gateway bị cạn kiệt dần cho đến khi hệ điều hành (hoặc Docker) tự động kill tiến trình Gateway.
* **Cách giả lập hiện tượng trong dự án này**: Sử dụng một công cụ benchmark (như Apache Bench `ab` hoặc `k6`) để gửi liên tiếp các request HTTP có kích thước Payload (body hoặc header) rất lớn tới API Gateway nhằm làm tràn bộ nhớ đệm Direct Buffer của Netty.

# Danh sách 10 Lỗi Hệ thống Thường gặp (Microservices)

Dưới đây là chi tiết về 10 lỗi hệ thống phổ biến đối với kiến trúc của dự án hiện tại (Spring Boot, Eureka, Spring Cloud Gateway, Feign Client, PostgreSQL, MongoDB, Redis và RabbitMQ).

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

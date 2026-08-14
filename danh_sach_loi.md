# Danh sách 10 Lỗi Hệ thống Thường gặp (Microservices)

Dưới đây là chi tiết về 10 lỗi hệ thống phổ biến đối với kiến trúc của dự án hiện tại (Spring Boot, Eureka, Spring Cloud Gateway, Feign Client, PostgreSQL, MongoDB, Redis và RabbitMQ).

---

### 1. Sập Eureka Server hoặc Mất Đồng Bộ Service Registry
* **Hiện tượng**: `discovery-client-service` (Eureka) bị sập hoặc gặp sự cố kết nối mạng khiến các service không thể cập nhật hoặc gửi heartbeat.
* **Hậu quả**: API Gateway và các Feign Client (`lb://`) không thể phân giải được IP của các service khác, dẫn đến lỗi `503 Service Unavailable` hoặc lỗi Timeout hàng loạt khi gọi API chéo.

### 2. Lỗi Sụp Đổ Dây Chuyền (Cascading Failure) do thiếu Circuit Breaker
* **Hiện tượng**: `account-service` hoặc `credit-card-service` gọi đồng bộ (Synchronous HTTP) sang `user-service` qua Feign Client. Khi `user-service` bị chậm hoặc nghẽn, các request bên gọi sẽ bị treo để chờ phản hồi.
* **Hậu quả**: Cạn kiệt luồng xử lý (Thread Pool Exhaustion) ở service gọi khiến nó cũng bị sập theo. Cần tích hợp cơ chế Circuit Breaker (như Resilience4j) để ngắt mạch kịp thời khi service đích gặp sự cố.

### 3. Bất Đồng Nhất Dữ Liệu Phân Tán (Distributed Data Inconsistency)
* **Hiện tượng**: Một giao dịch nghiệp vụ nghiệp vụ trải dài qua nhiều service (ví dụ: tạo thẻ tín dụng cần gọi cả `user-service` và `bank-service`).
* **Hậu quả**: Nếu bước gọi sang `bank-service` thất bại sau khi dữ liệu ở `credit-card-service` đã được lưu, hệ thống sẽ bị sai lệch dữ liệu (không thể rollback tự động bằng `@Transactional` thông thường). Cần áp dụng **Saga Pattern** hoặc **Transactional Outbox**.

### 4. Nghẽn Hàng Đợi RabbitMQ (Queue Congestion/OOM)
* **Hiện tượng**: Các service đẩy log qua RabbitMQ (`logs.queue`) để `log-service` lưu vào MongoDB. Nếu `log-service` hoặc MongoDB bị chậm/sập, các tin nhắn log sẽ tích tụ trong queue.
* **Hậu quả**: Hàng đợi bị tràn bộ nhớ (Memory High Watermark), khiến RabbitMQ chặn (block) toàn bộ kết nối từ các service gửi log, làm nghẽn toàn bộ hệ thống nghiệp vụ chính.

### 5. Lỗi Đồng Bộ Cache Redis (Cache Inconsistency)
* **Hiện tượng**: Các service sử dụng Redis để cache thông tin người dùng/tài khoản. Dữ liệu dưới PostgreSQL thay đổi nhưng cache trong Redis chưa được xóa hoặc cập nhật do lỗi kết nối mạng tạm thời.
* **Hậu quả**: Client liên tục đọc phải dữ liệu cũ (stale data) từ cache, dẫn đến sai lệch thông tin giao dịch hoặc số dư hiển thị.

### 6. Điểm Nghẽn Duy Nhất (Single Point of Failure - SPOF) tại Gateway
* **Hiện tượng**: Chỉ chạy duy nhất 1 instance của `api-gateway-service`.
* **Hậu quả**: Khi Gateway bị sập hoặc quá tải luồng định tuyến, toàn bộ các API của hệ thống từ bên ngoài gọi vào đều bị chặn hoàn toàn, dù các service nghiệp vụ bên trong vẫn hoạt động bình thường.

### 7. Cạn Kiệt Connection Pool của Database (PostgreSQL)
* **Hiện tượng**: Các service kết nối chung vào cùng một container `postgres-db` (dùng chung tài nguyên máy chủ).
* **Hậu quả**: Khi một service chạy các query nặng, không tối ưu hoặc bị rò rỉ kết nối (connection leak), nó sẽ chiếm dụng hết tài nguyên của database dùng chung, làm treo kết nối và lỗi Timeout ở toàn bộ các service khác.

### 8. Chế độ Tự Bảo Vệ của Eureka (Eureka Self-Preservation)
* **Hiện tượng**: Khi mất kết nối mạng tạm thời giữa các server, Eureka tự động kích hoạt chế độ "Self-Preservation" để tránh xóa nhầm các service instance vẫn còn sống.
* **Hậu quả**: Eureka sẽ giữ lại cả những instance đã thực sự chết. API Gateway vẫn tiếp tục điều hướng request vào các instance chết này, tạo ra lỗi kết nối chập chờn cho người dùng cuối.

### 9. Thiếu Truy Vết Phân Tán (Lack of Distributed Tracing)
* **Hiện tượng**: Một lỗi xảy ra khi gọi chuỗi API qua Gateway -> Account Service -> User Service.
* **Hậu quả**: Việc debug cực kỳ khó khăn vì log nằm phân tán ở nhiều container khác nhau và không có một ID định danh chung để liên kết hành trình của request. Cần tích hợp thêm **Sleuth/Micrometer Tracing** và **Zipkin** để gán `Trace ID` đi xuyên suốt các service.

### 10. Lỗi Phiên Bản và Cấu Hình Feign Client Không Khớp
* **Hiện tượng**: Tên các service được đăng ký trên Eureka bằng chữ thường (như `bank-service`) nhưng Feign Client hoặc cấu hình Gateway map tên bằng chữ hoa hoặc ngược lại mà không đồng nhất.
* **Hậu quả**: Feign client báo lỗi `No instances available for bank-service` ngay cả khi service đang hoạt động bình thường, do cơ chế phân giải tên bị sai lệch cấu hình.

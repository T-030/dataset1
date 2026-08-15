# Danh Sách & Mô Tả Các Kịch Bản Lỗi Hệ Thống (Banking Microservices)

Tài liệu này tổng hợp và mô tả chi tiết các kịch bản lỗi giả lập/mô phỏng sự cố (Fault Injection) trong hệ thống Banking Microservices. Các kịch bản lỗi hiện tại được tập trung chủ yếu vào **`user-service`** để phục vụ việc diễn tập và thử nghiệm kiểm thử chịu lỗi mà không làm ảnh hưởng đến tính ổn định của các dịch vụ còn lại trong hệ thống.

---

## 1. Danh Sách Các Lỗi Mô Phỏng (Tập trung tại `user-service`)

### 🔴 Lỗi 1: Sai Tên Service & Thất Bại Đăng Ký Eureka (Service Registration & Discovery Failure)
- **Service chịu ảnh hưởng**: `user-service`
- **Vị trí cấu hình**: [`user-service/src/main/resources/application.properties`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/src/main/resources/application.properties#L5-L8)
- **Cách mô phỏng**: Comment dòng `spring.application.name=user-service` và bỏ comment dòng `spring.application.name=user-service-broken`.
- **Nguyên nhân & Cơ chế**: Khi đổi tên service thành `user-service-broken`, service đăng ký với Eureka Server dưới tên mới. Các service khác (`account-service`, `credit-card-service`, `invoice-service`) gọi `user-service` thông qua Feign Client với tên `user-service` sẽ không tìm thấy instance khả dụng.
- **Hậu quả**: Phản hồi lỗi `500 Internal Server Error` hoặc `No instances available for user-service` khi thực hiện các thao tác liên dịch vụ (ví dụ: tạo tài khoản, tạo thẻ tín dụng).

---

### 🔴 Lỗi 2: Sụp Đổ Dây Chuyền Lan Truyền Qua Feign (Cascading Failure & Timeout)
- **Service chịu ảnh hưởng**: `user-service` (nguồn gốc), kéo theo `account-service`, `credit-card-service`, `invoice-service`.
- **Vị trí code**: [`UserController.java`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/src/main/java/unaldi/userservice/controller/UserController.java#L57-L63)
- **Cách mô phỏng**: Bỏ comment đoạn `Thread.sleep(10000);` tại API `GET /api/v1/users/{userId}`.
- **Nguyên nhân & Cơ chế**: Thao tác lấy thông tin người dùng bị cố tình trì hoãn 10 giây. Khi các service khác gọi sang `user-service` qua Feign Client để kiểm tra thông tin user trước khi tạo account/card, các request sẽ bị treo và ném ra lỗi `FeignException: Read timed out`.
- **Hậu quả**: Lan truyền độ trễ cao và gây sụp đổ dây chuyền (Cascading Failure), cạn kiệt worker thread pool của API Gateway và các service gọi downstream.

---

### 🔴 Lỗi 3: Cạn Kiệt Database Connection Pool (HikariCP Pool Exhaustion)
- **Service chịu ảnh hưởng**: `user-service`
- **Vị trí cấu hình**: [`user-service/src/main/resources/application.properties`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/src/main/resources/application.properties#L32-L35)
- **Cách mô phỏng**: Bỏ comment các dòng cấu hình Hikari:
  ```properties
  spring.datasource.hikari.maximum-pool-size=1
  spring.datasource.hikari.connection-timeout=2000
  ```
- **Nguyên nhân & Cơ chế**: Giới hạn số lượng kết nối tối đa xuống database PostgreSQL chỉ còn **1 connection** và thời gian chờ kết nối tối đa là 2 giây.
- **Hậu quả**: Khi có từ 2 request đồng thời gửi đến `user-service`, request thứ hai không thể lấy được database connection trong vòng 2 giây và ném ra ngoại lệ `SQLTransientConnectionException: Connection is not available, request timed out after 2000ms`.

---

### 🔴 Lỗi 4: Bất Đồng Nhất Dữ Liệu Cache Redis (Cache Inconsistency)
- **Service chịu ảnh hưởng**: `user-service`
- **Vị trí code**: [`UserServiceImpl.java`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/src/main/java/unaldi/userservice/service/concretes/UserServiceImpl.java#L63-L68)
- **Cách mô phỏng**: Thay đổi điều kiện `@CacheEvict(value = Caches.USERS_CACHE, allEntries = true, condition = "false")` hoặc comment các annotation `@CachePut` / `@CacheEvict` ở hàm `update()`.
- **Nguyên nhân & Cơ chế**: Khi cập nhật thông tin người dùng (`PUT /api/v1/users`), dữ liệu mới đã được lưu thành công vào PostgreSQL Database, nhưng Redis Cache lại không được xóa hoặc cập nhật.
- **Hậu quả**: Dữ liệu trong database và cache bị lệch nhau. Các truy vấn đọc `GET /api/v1/users/{id}` tiếp theo vẫn lấy dữ liệu cũ từ Redis Cache thay vì dữ liệu mới trong Database.

---

### 🔴 Lỗi 5: Bám Bẩn Cache Do Race Condition / Cache Stampede (Cache Pollution)
- **Service chịu ảnh hưởng**: `user-service`
- **Vị trí code**: [`UserServiceImpl.java`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/src/main/java/unaldi/userservice/service/concretes/UserServiceImpl.java#L77-L85)
- **Cách mô phỏng**: Bỏ comment đoạn `Thread.sleep(1000);` trong hàm `update()`.
- **Nguyên nhân & Cơ chế**: Annotation `@CacheEvict` sẽ xóa cache Redis ngay khi vừa gọi vào hàm `update()`. Sau đó thread xử lý bị cho ngủ 1 giây trước khi lưu dữ liệu mới vào DB. Trong khoảng thời gian 1 giây này, nếu có request `GET` đọc user được gửi đến, nó sẽ thấy Redis trống, đọc dữ liệu cũ chưa update từ DB và lưu lại vào Redis Cache.
- **Hậu quả**: Dữ liệu cũ bị nạp lại và "bám bẩn" (Cache Pollution) vào Redis ngay cả sau khi giao dịch `update()` hoàn tất thành công.

---

## 2. Hướng Dẫn Bật / Tắt Các Lỗi Để Kiểm Thử

- Tất cả các lỗi trên được thiết kế dưới dạng **toggle comments** (chú thích bật/tắt trong file nguồn).
- **Để hệ thống ở trạng thái hoạt động bình thường (Healthy)**: Đảm bảo các đoạn mã giả lập lỗi trong `application.properties`, `UserServiceImpl.java` và `UserController.java` được comment lại.
- **Để thử nghiệm kịch bản lỗi cụ thể**: Bỏ comment tại kịch bản tương ứng và khởi động/build lại `user-service`.

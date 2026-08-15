# Danh Sách & Mô Tả Các Kịch Bản Lỗi Hệ Thống (Khai Báo Dạng YAML)

Tài liệu này tổng hợp và mô tả chi tiết 5 kịch bản lỗi hệ thống (Fault Injection Spec) được khai báo dạng **YAML** trực tiếp trong file cấu hình catalog [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml). 

Mô hình phân tích (Model / Scanner) có thể đọc cấu hình YAML này để nhận diện toàn bộ 5 lỗi được tập trung tại **`user-service`**, trong khi các service còn lại giữ trạng thái khỏe mạnh (healthy).

---

## 1. Cấu Trúc Khai Báo Lỗi Dạng YAML trong `user-service/catalog-info.yaml`

File [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml) chứa khai báo chi tiết dưới mục `spec.errors`:

```yaml
spec:
  type: service
  id: user-service
  name: User Service

  errors:
    - id: ERR-USER-001
      type: ServiceRegistrationError
      title: "Lỗi 1: Sai tên Service / Eureka Feign Mismatch"
      description: "Service đăng ký tên bất hợp lệ (user-service-broken) lên Eureka Server, dẫn đến Feign Client từ các service khác (account-service, credit-card-service, invoice-service) gọi sang bị lỗi No instances available."
      status: active_simulation
      target_config:
        file: user-service/src/main/resources/application.properties
        property: spring.application.name
        faulty_value: user-service-broken
        expected_value: user-service

    - id: ERR-USER-002
      type: CascadingFailure
      title: "Lỗi 2: Sụp đổ dây chuyền (Cascading Failure & Timeout)"
      description: "API GET /api/v1/users/{userId} bị độ trễ 10000ms (Thread.sleep), lan truyền lỗi Timeout qua Feign sang các service phụ thuộc và gây nghẽn toàn hệ thống."
      status: active_simulation
      target_code:
        file: user-service/src/main/java/unaldi/userservice/controller/UserController.java
        method: findById
        fault_type: ResponseLatencyTimeout

    - id: ERR-USER-003
      type: DatabasePoolExhaustion
      title: "Lỗi 3: Cạn kiệt Database Connection Pool (HikariCP)"
      description: "Cấu hình HikariCP kết nối PostgreSQL bị giới hạn quá thấp (maximum-pool-size=1, connection-timeout=2000ms), gây sập connection pool khi có từ 2 request đồng thời."
      status: active_simulation
      target_config:
        file: user-service/src/main/resources/application.properties
        property: spring.datasource.hikari.maximum-pool-size
        faulty_value: 1
        connection_timeout_ms: 2000

    - id: ERR-USER-004
      type: CacheInconsistency
      title: "Lỗi 4: Bất đồng nhất dữ liệu Cache Redis (Cache Inconsistency)"
      description: "Annotation @CacheEvict bị điều kiện sai (condition='false') hoặc bị vô hiệu hóa ở hàm update(), khiến PostgreSQL DB đã cập nhật nhưng Redis Cache giữ nguyên dữ liệu cũ."
      status: active_simulation
      target_code:
        file: user-service/src/main/java/unaldi/userservice/service/concretes/UserServiceImpl.java
        method: update
        fault_type: DisableCacheEviction

    - id: ERR-USER-005
      type: CachePollutionRaceCondition
      title: "Lỗi 5: Race Condition / Bám bẩn Cache (Cache Pollution & Stampede)"
      description: "Độ trễ 1000ms trong hàm update() sau khi vừa evict cache khiến các request GET song song đọc dữ liệu cũ từ DB và nạp đè dữ liệu bẩn vào Redis."
      status: active_simulation
      target_code:
        file: user-service/src/main/java/unaldi/userservice/service/concretes/UserServiceImpl.java
        method: update
        fault_type: CacheStampedeSleep
```

---

## 2. Chi Tiết 5 Kịch Bản Lỗi Khai Báo Trong YAML

### 🔴 1. ERR-USER-001: ServiceRegistrationError
- **File YAML**: [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml)
- **Tóm tắt**: Đổi `spring.application.name=user-service-broken`. Eureka không tìm thấy `user-service`.

### 🔴 2. ERR-USER-002: CascadingFailure
- **File YAML**: [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml)
- **Tóm tắt**: API `GET /api/v1/users/{userId}` phản hồi trễ 10.000ms gây Read Timeout lan truyền qua Feign Client sang các service khác (`account-service`, `credit-card-service`, `invoice-service`).

### 🔴 3. ERR-USER-003: DatabasePoolExhaustion
- **File YAML**: [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml)
- **Tóm tắt**: Cấu hình HikariCP `maximum-pool-size=1` và `connection-timeout=2000` làm cạn kiệt DB Connection Pool khi có từ 2 request gửi đồng thời.

### 🔴 4. ERR-USER-004: CacheInconsistency
- **File YAML**: [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml)
- **Tóm tắt**: `@CacheEvict` với `condition="false"` trong `UserServiceImpl.update()` khiến Redis Cache và PostgreSQL Database bị lệch dữ liệu.

### 🔴 5. ERR-USER-005: CachePollutionRaceCondition
- **File YAML**: [`user-service/catalog-info.yaml`](file:///c:/Users/Admin/Data_Banking_14_8/user-service/catalog-info.yaml)
- **Tóm tắt**: Trì hoãn 1000ms trong hàm `update()` sau khi xóa cache khiến request đọc đồng thời ghi dữ liệu cũ chưa update ngược trở lại Redis Cache.

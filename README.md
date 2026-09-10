# Meeting Platform - Real-time Meeting Backend

<p align="center">
  <strong>Backend API cho nền tảng họp trực tuyến, lập lịch, ghi hình và xử lý nội dung bằng AI.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.10-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5.10">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL 16">
  <img src="https://img.shields.io/badge/MongoDB-7.0-47A248?style=flat-square&logo=mongodb&logoColor=white" alt="MongoDB 7">
  <img src="https://img.shields.io/badge/Redis-7.2-DC382D?style=flat-square&logo=redis&logoColor=white" alt="Redis 7.2">
  <img src="https://img.shields.io/badge/LiveKit-WebRTC-111111?style=flat-square&logo=webrtc&logoColor=white" alt="LiveKit WebRTC">
</p>

---

## Giới thiệu

**Meeting Platform** là backend Spring Boot cho một hệ thống họp trực tuyến thời gian thực. Ứng dụng cung cấp REST API và WebSocket/STOMP để quản lý phòng họp, người tham gia, phòng chờ, lịch họp, lời mời, bình chọn và các thao tác điều phối của host.

Hệ thống sử dụng **LiveKit** cho media WebRTC, **Keycloak** cho xác thực JWT, **MinIO** để lưu bản ghi, **RabbitMQ** cho pipeline xử lý bất đồng bộ và kết hợp **PostgreSQL**, **MongoDB**, **Redis** theo từng loại dữ liệu. Sau khi cuộc họp được ghi hình, người dùng có thể gửi yêu cầu tạo transcript/tóm tắt; backend quản lý job, lưu kết quả và cung cấp API đọc nội dung theo trang.

## Tính năng chính

### Người dùng và bảo mật

- Đăng nhập một lần qua Keycloak, xác thực OAuth2 Resource Server bằng JWT.
- Tự động đồng bộ hồ sơ người dùng từ claim trong token.
- Quản lý hồ sơ cá nhân và phân quyền thao tác theo vai trò trong cuộc họp.
- Cấu hình CORS, WebSocket origin và session stateless.

### Cuộc họp thời gian thực

- Tạo cuộc họp tức thời hoặc lên lịch trước.
- Bắt đầu, cập nhật, kết thúc hoặc hủy cuộc họp theo vòng đời rõ ràng.
- Tham gia/rời phòng, phòng chờ, phê duyệt hoặc từ chối người tham gia.
- Theo dõi presence bằng heartbeat, danh sách người tham gia cập nhật theo thời gian thực.
- Giơ tay, reaction, poll và các lệnh của host như mute, kick hoặc kết thúc cho tất cả.
- Cấp token LiveKit để truyền âm thanh, video và chia sẻ màn hình qua WebRTC.
- STUN/TURN hỗ trợ kết nối trong các điều kiện mạng khác nhau.

### Lịch họp và lời mời

- Lịch cá nhân và danh sách cuộc họp sắp tới.
- Mời người tham gia bằng email, chấp nhận hoặc từ chối lời mời.
- Gửi email qua Resend kèm lịch `.ics`.
- Transactional outbox giúp việc phát thông báo bền vững và có retry.

### Ghi hình và AI

- Bắt đầu/dừng ghi hình bằng LiveKit Egress.
- Lưu artifact trong MinIO; quản lý danh sách bản ghi theo người dùng hoặc cuộc họp.
- Xóa mềm, khôi phục, xóa vĩnh viễn và tự dọn thùng rác theo thời gian lưu giữ.
- Tạo transcript tiếng Việt và bản tóm tắt từ recording.
- Gửi yêu cầu tóm tắt video YouTube và đọc transcript theo cursor.
- Pipeline RabbitMQ có publisher confirm, retry theo backoff và dead-letter queue.
- Metadata/job được lưu trong PostgreSQL; transcript và nội dung dài được lưu trong MongoDB.

## Công nghệ sử dụng

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5.10, Maven | REST API và nghiệp vụ |
| Security | Spring Security, OAuth2 Resource Server, Keycloak 23 | JWT, SSO và đồng bộ người dùng |
| Realtime | WebSocket, STOMP, Redis | Signaling, heartbeat và sự kiện cuộc họp |
| Media | LiveKit Server 1.13.6, LiveKit Egress 1.12.0, coturn | WebRTC, recording và TURN relay |
| Relational data | PostgreSQL 16, Spring Data JPA, Flyway | Dữ liệu nghiệp vụ, migration và AI job |
| Document data | MongoDB 7, Spring Data MongoDB | Transcript segment, poll và nội dung AI |
| Messaging | RabbitMQ 3.13 | Xử lý AI bất đồng bộ, retry và DLQ |
| Object storage | MinIO | Recording, transcript và summary artifact |
| API docs | Springdoc OpenAPI / Swagger UI | Khám phá và thử API |
| Notification | Resend, transactional outbox | Email lời mời và file lịch ICS |
| Deployment | Docker, Docker Compose, Nginx, Cloudflare Tunnel | Đóng gói và triển khai |

## Yêu cầu tiên quyết

Để chạy toàn bộ hệ thống bằng Docker:

- **Docker Engine/Desktop** hỗ trợ Docker Compose v2.
- Nên dành ít nhất **8 GB RAM khả dụng** cho toàn bộ stack, đặc biệt LiveKit Egress.
- Các cổng local chưa bị chiếm: `8080`, `8081`, `7880`, `7881`, `9000`, `9001`, `5432`, `6379`, `27017`, `5672`, `15672`, `3478`.

Để chạy backend trực tiếp trên máy:

- **JDK 21+**.
- Không bắt buộc cài Maven toàn cục vì dự án có Maven Wrapper.
- PostgreSQL, Redis, MongoDB, RabbitMQ, Keycloak, LiveKit và MinIO đang hoạt động.

## Cài đặt và chạy dự án

### 1. Clone repository

```bash
git clone https://github.com/manhduc205/Meeting-Platform.git
cd Meeting-Platform
```

### 2. Tạo file cấu hình môi trường

Tạo `.env` ở thư mục gốc. Có thể bắt đầu từ file mẫu:

```powershell
Copy-Item .env.production.example .env
```

```bash
cp .env.production.example .env
```

Cấu hình local tối thiểu khi backend chạy trong Docker:

```env
KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/meeting-realm
KEYCLOAK_JWK_URI=http://keycloak:8080/realms/meeting-realm/protocol/openid-connect/certs
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200
WS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:4200
LIVEKIT_PUBLIC_URL=ws://localhost:7880

RESEND_API_KEY=
RESEND_FROM_EMAIL=Meeting Platform <onboarding@resend.dev>
PUBLIC_APP_URL=http://localhost:4200
```

> [!WARNING]
> Các tài khoản và secret có sẵn trong cấu hình Docker chỉ dành cho môi trường phát triển. Hãy thay toàn bộ password, LiveKit key, TURN credential và access key trước khi triển khai công khai; không commit file `.env` thật.

### 3. Khởi động hệ thống bằng Docker Compose

Chạy stack local, không bật Cloudflare Tunnel:

```bash
docker compose up -d --build postgres redis mongodb rabbitmq keycloak livekit livekit-egress minio coturn backend
```

Kiểm tra trạng thái container:

```bash
docker compose ps
```

Xem log backend:

```bash
docker compose logs -f backend
```

### 4. Cấu hình Keycloak lần đầu

Docker Compose khởi tạo Keycloak nhưng không import sẵn realm. Truy cập [Keycloak Admin Console](http://localhost:8080), đăng nhập bằng tài khoản development trong `docker-compose.yml`, sau đó:

1. Tạo realm `meeting-realm`.
2. Tạo client cho frontend và khai báo đúng redirect URI/web origin của frontend.
3. Tạo user thử nghiệm hoặc kết nối identity provider mong muốn.
4. Đảm bảo access token có các claim cơ bản như `sub`, `preferred_username`, `email` và `name`.

### 5. Chạy backend trực tiếp khi phát triển

Khởi động các dịch vụ hạ tầng trước, bỏ service `backend`, rồi chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Trên macOS/Linux:

```bash
./mvnw spring-boot:run
```

Khi backend chạy ngoài Docker, đổi các hostname nội bộ như `keycloak`, `livekit` và `minio` trong `.env` thành `localhost` tương ứng.

Ứng dụng mặc định chạy tại [http://localhost:8081](http://localhost:8081).


## Cấu trúc dự án

```text
Meeting-Platform/
├── src/
│   ├── main/
│   │   ├── java/com/manhduc205/
│   │   │   ├── meetingplatform/
│   │   │   │   ├── configurations/  # Security, WebSocket, Redis, LiveKit, Swagger
│   │   │   │   ├── controllers/     # REST và STOMP controllers
│   │   │   │   ├── models/          # JPA entities và MongoDB documents
│   │   │   │   ├── repositories/    # PostgreSQL/MongoDB repositories
│   │   │   │   ├── security/        # Chuyển đổi JWT/role từ Keycloak
│   │   │   │   ├── services/        # Nghiệp vụ meeting, media, recording, poll
│   │   │   │   └── exceptions/      # Exception và global error handler
│   │   │   ├── AI_application/       # Job, transcript và summary APIs/services
│   │   │   └── routing/              # RabbitMQ topology, publisher và consumer
│   │   └── resources/
│   │       ├── db/migration/          # Flyway migrations
│   │       └── application.yaml       # Cấu hình ứng dụng
│   └── test/                          # Unit và integration/contract tests
├── nginx/meeting.conf.example         # Reverse proxy mẫu cho production
├── docker-compose.yml                 # Toàn bộ hạ tầng local
├── Dockerfile                         # Multi-stage build Java 21
├── livekit.yaml                       # LiveKit Server configuration
├── egress.yaml                        # LiveKit Egress configuration
├── .env.production.example            # Biến môi trường mẫu
├── API_DOC.md                          # Tài liệu API chi tiết
├── SCHEDULING_API.md                   # API lịch họp và invitation
├── DEPLOYMENT.md                       # Hướng dẫn triển khai
└── pom.xml                             # Maven dependencies/build
```

## Kiểm thử và build

Chạy test trên Windows:

```powershell
.\mvnw.cmd test
```

Chạy test trên macOS/Linux:

```bash
./mvnw test
```

Build file JAR:

```powershell
.\mvnw.cmd clean package
```

Artifact được tạo trong thư mục `target/`.

## Tài liệu liên quan

- [API_DOC.md](API_DOC.md): mô tả REST API, WebSocket, data model và luồng nghiệp vụ.
- [SCHEDULING_API.md](SCHEDULING_API.md): lịch họp, lời mời, calendar feed và transactional outbox.
- [DEPLOYMENT.md](DEPLOYMENT.md): cấu hình local/production, reverse proxy, LiveKit và firewall.
- [nginx/meeting.conf.example](nginx/meeting.conf.example): cấu hình Nginx tham khảo.

## Lưu ý khi triển khai production

- Dùng HTTPS/WSS và secret manager; không tái sử dụng credential development.
- `LIVEKIT_HOST` là địa chỉ backend dùng nội bộ, còn `LIVEKIT_PUBLIC_URL` phải truy cập được từ trình duyệt.
- Reverse proxy phải giữ WebSocket upgrade headers cho STOMP và LiveKit signaling.
- Mở UDP `50000-50100` cho LiveKit và relay range đã cấu hình cho coturn.
- Nếu dùng Cloudflare, endpoint LiveKit media cần cấu hình phù hợp với UDP; Cloudflare Tunnel không vận chuyển RTP/UDP.
- Sao lưu PostgreSQL, MongoDB và MinIO trước khi migration hoặc nâng cấp.

## Liên hệ

- **Tác giả:** Nguyễn Mạnh Đức
- **Email:** nguyenduc2005qo@email.com
- **LinkedIn:** [Nguyễn Mạnh Đức](https://www.linkedin.com/in/%C4%91%E1%BB%A9c-nguy%E1%BB%85n-536199317/)

---

<p align="center">
  Made with Spring Boot, LiveKit and a lot of coffee.
</p>

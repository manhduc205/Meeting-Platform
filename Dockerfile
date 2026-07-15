# Stage 1: Build mã nguồn
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy file cấu hình thư viện trước để tận dụng cache của Docker
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ mã nguồn và tiến hành build (Bỏ qua chạy Test để deploy nhanh)
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Môi trường chạy thực tế (Runtime)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Đặt Timezone chuẩn giờ Việt Nam cho Server
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime \
    && echo "Asia/Ho_Chi_Minh" > /etc/timezone

# Lấy file .jar đã build từ Stage 1 sang
COPY --from=builder /build/target/*.jar app.jar

# Mở port cho Backend
EXPOSE 8081

# Lệnh khởi chạy
ENTRYPOINT ["java", "-jar", "app.jar"]
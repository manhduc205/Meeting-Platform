# Stage 1: Build mã nguồn với Maven & Java 21
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy file pom.xml trước để tận dụng Docker Layer Cache
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ source code và build (Bỏ qua test để tăng tốc)
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime Environment với Java 21 JRE (Siêu nhẹ Alpine)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Cài đặt Timezone chuẩn Việt Nam (Ho_Chi_Minh)
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime \
    && echo "Asia/Ho_Chi_Minh" > /etc/timezone

# Copy file .jar đã được build từ Stage 1 sang
COPY --from=builder /build/target/*.jar app.jar

# Mở port cho Backend Spring Boot
EXPOSE 8081

# Lệnh khởi chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
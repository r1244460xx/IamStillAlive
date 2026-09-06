# Stage 1: Build application with Gradle
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Gradle wrapper and configuration
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Fix potential Windows CRLF issues and grant execution permissions
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Pre-fetch dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build runnable JAR
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV TZ=Asia/Taipei

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

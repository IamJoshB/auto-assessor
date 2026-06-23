# Multi-stage build: Separate compile-time from runtime
# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and project files
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon 2>&1 | grep -v "^Download" || true

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 appgroup && \
    adduser -D -u 1001 -G appgroup appuser

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership to appuser
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose Spring Boot default port
EXPOSE 8080

# Health check using curl to /actuator/health
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

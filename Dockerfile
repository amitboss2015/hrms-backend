# Multi-stage build for ChandraHR Backend
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user for security
RUN groupadd -r hrms && useradd -r -g hrms hrms

# Copy JAR from builder stage
COPY --from=builder /app/target/hrms-backend-0.0.1-SNAPSHOT.jar app.jar

# Set ownership
RUN chown -R hrms:hrms /app

USER hrms

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]

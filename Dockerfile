# Multi-stage build for FitPub application

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy POM file first for better layer caching
COPY pom.xml .

# Download dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r fitpub && useradd -r -g fitpub fitpub

# Create directories for uploads and logs
RUN mkdir -p /app/uploads /app/logs && \
    chown -R fitpub:fitpub /app

# Copy the built artifact from builder stage
COPY --from=builder /build/target/*.jar /app/fitpub.jar

# Change ownership
RUN chown fitpub:fitpub /app/fitpub.jar

# Switch to non-root user
USER fitpub

# Expose application port
EXPOSE 8080

# Environment variables
ENV REGISTRATION_ENABLED=true

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/app/fitpub.jar"]

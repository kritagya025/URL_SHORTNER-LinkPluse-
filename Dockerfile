# ==============================================================================
# STAGE 1: Build Stage (Compile Spring Boot source code to executable JAR)
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# Set the working directory inside the build container
WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching for Maven dependencies
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy the rest of the application source code
COPY src ./src

# Build the production JAR file, skipping tests (tests ran separately during CI)
RUN mvn clean package -DskipTests

# ==============================================================================
# STAGE 2: Runtime Stage (Lightweight production image)
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner

# Create a non-root system user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the compiled JAR file from the builder stage
COPY --from=builder /app/target/url-shortener-1.0.0-SNAPSHOT.jar app.jar

# Change ownership of the runtime directory to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port 8080 (the port Spring Boot runs on inside the container)
EXPOSE 8080

# Configure container startup command
ENTRYPOINT ["java", "-jar", "app.jar"]

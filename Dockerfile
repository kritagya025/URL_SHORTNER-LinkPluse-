# ==============================================================================
# STAGE 1: Build Stage — Compile Spring Boot source code to executable JAR
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

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
# STAGE 2: JRE Extraction — Build a minimal custom JRE with jlink
# ==============================================================================
FROM eclipse-temurin:17-jdk-alpine AS jre-builder

RUN apk add --no-cache binutils

# Analyze the Spring Boot fat JAR to find required JDK modules, then build a
# stripped-down JRE containing only those modules.
# We use a comprehensive module list proven for Spring Boot + embedded Tomcat:
COPY --from=builder /app/target/url-shortener-1.0.0-SNAPSHOT.jar /tmp/app.jar
RUN MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,\
java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,\
java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,\
java.xml,jdk.crypto.ec,jdk.naming.dns,jdk.unsupported" && \
    jlink \
      --add-modules "$MODULES" \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress=2 \
      --output /opt/jre-minimal

# ==============================================================================
# STAGE 3: Runtime Stage — Minimal production image
# ==============================================================================
FROM alpine:3.21 AS runner

# Install only the minimal runtime dependencies the JRE needs
RUN apk add --no-cache ca-certificates tzdata

# Use the custom minimal JRE built by jlink
ENV JAVA_HOME=/opt/jre-minimal
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=jre-builder /opt/jre-minimal $JAVA_HOME

# Create a non-root system user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the compiled JAR from the builder stage — use --chown to avoid an extra
# layer that duplicates the JAR (the old RUN chown added ~47 MB)
COPY --from=builder --chown=appuser:appgroup /app/target/url-shortener-1.0.0-SNAPSHOT.jar app.jar

# Switch to non-root user
USER appuser

# Expose port 8080 (the port Spring Boot runs on inside the container)
EXPOSE 8080

# Configure container startup with memory-aware JVM flags
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

# Dockerfile for building Laret native binary for Linux
FROM ghcr.io/graalvm/graalvm-community:24

# Install build dependencies
RUN microdnf install -y findutils tar gzip

# Set working directory
WORKDIR /build

# Copy only necessary files for build
COPY gradle ./gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src ./src

# Make gradlew executable
RUN chmod +x ./gradlew

# Build native binary
RUN ./gradlew nativeCompile --no-daemon

# Copy binary to output with executable permissions
CMD ["sh", "-c", "cp /build/build/native/nativeCompile/laret /output/laret && chmod +x /output/laret"]

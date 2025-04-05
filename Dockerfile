# Use Debian-based OpenJDK for compatibility with Qiskit
FROM openjdk:17-slim

# Set working directory
WORKDIR /app

# Copy the built JAR from web module
COPY web/target/plank-db.jar /app/plank-db.jar

# Copy Python scripts
COPY python/ /app/python/

# Install Python 3, pip, and system build tools
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-venv \
    build-essential \
    curl \
    git \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Qiskit and IBM Runtime SDK
RUN pip3 install --upgrade pip && \
    pip3 install qiskit qiskit-ibm-runtime

# Expose Spring Boot port
EXPOSE 8085

# Use ENV vars for profile
ENV SPRING_PROFILES_ACTIVE=default

# Run Spring Boot app
ENTRYPOINT ["java", "-jar", "plank-db.jar"]

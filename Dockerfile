# === Stage 1: Build the JAR using Maven ===
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

# === Stage 2: Runtime container with Python + Java ===
FROM openjdk:17-slim
WORKDIR /app

# Copy the compiled JAR from the builder stage
COPY --from=builder /build/web/target/plank-db.jar ./plank-db.jar

# Copy Python scripts and .env
COPY python/ ./python/
COPY .env /app/.env

# Install system dependencies for Qiskit
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    apt-transport-https \
    ca-certificates \
    python3 \
    python3-pip \
    python3-venv \
    build-essential \
    gcc \
    g++ \
    libopenblas-dev \
    liblapack-dev \
    libomp-dev \
    git && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Upgrade pip and install Qiskit & dependencies
RUN pip3 install --no-cache-dir --upgrade pip && \
    pip3 install --no-cache-dir \
    "qiskit~=1.0" \
    "qiskit-aer" \
    "qiskit-algorithms" \
    "qiskit-ibm-runtime>=0.24.0" \
    "tweedledum" \
    "python-dotenv" \
    "numpy==1.26.4"

# Python script path
ENV PYTHONPATH=/app/python

ENV SPRING_PROFILES_ACTIVE=default

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "plank-db.jar"]
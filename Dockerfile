# ✅ Base image with OpenJDK 17 (for Spring Boot)
FROM openjdk:17-slim

# ✅ Set working directory inside the container
WORKDIR /app

# ✅ Copy Java backend (REST API)
COPY web/target/plank-db.jar ./plank-db.jar

# ✅ Copy Python code (Grover, etc.)
COPY python/ ./python/

COPY .env /app/.env

# ✅ Install essential Debian packages
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

# ✅ Upgrade pip (recommended by Qiskit)
RUN pip3 install --no-cache-dir --upgrade pip

# ✅ Install Qiskit + IBM Runtime V2 support
RUN pip3 install --no-cache-dir \
    "qiskit~=1.0" \
    "qiskit-aer" \
    "qiskit-algorithms" \
    "qiskit-ibm-runtime>=0.24.0" \
    "tweedledum" \
    "python-dotenv" \
    "numpy==1.26.4"

# ✅ Set Python module path for import
ENV PYTHONPATH=/app/python

# ✅ Set Spring Boot profile if needed
ENV SPRING_PROFILES_ACTIVE=default

# ✅ Expose Java application port
EXPOSE 8085

# ✅ Launch Java REST API
ENTRYPOINT ["java", "-jar", "plank-db.jar"]

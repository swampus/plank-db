# === Stage 1: Build the JAR using Maven ===
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn -q -DskipTests clean package

# === Stage 2: Runtime with Java + Python (venv) ===
FROM openjdk:17-slim

# System deps for Qiskit/Aer & tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv \
    build-essential gcc g++ gfortran libgfortran5 \
    libopenblas-dev liblapack-dev libomp-dev \
    ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*

# Python venv
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1

# Python packages (pinned for stability)
RUN pip install --no-cache-dir --upgrade pip \
 && pip install --no-cache-dir \
    "numpy==1.26.4" \
    "qiskit~=1.0" \
    "qiskit-aer" \
    "qiskit-algorithms" \
    "qiskit-ibm-runtime>=0.24.0" \
    "tweedledum" \
    "python-dotenv"

# App files
WORKDIR /app
COPY --from=builder /build/web/target/plank-db.jar ./plank-db.jar
COPY python/ ./python/

# Make python scripts importable & point runner to venv python
ENV PYTHONPATH=/app/python \
    QUANTUM_PYTHON_EXEC=/opt/venv/bin/python \
    SPRING_PROFILES_ACTIVE=default

EXPOSE 8085

# Optional healthcheck if Spring Actuator is enabled:
# HEALTHCHECK --interval=30s --timeout=5s --retries=5 CMD \
#   curl -fsS http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "plank-db.jar"]
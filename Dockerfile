# === Stage 1: Build the JAR using Maven ===
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn -q -DskipTests clean package

# === Stage 2: Runtime with Java + Python (venv) ===
FROM openjdk:17-slim

# System deps required by Qiskit/Aer (runtime libs included)
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv \
    build-essential gcc g++ gfortran libgfortran5 \
    libopenblas-dev liblapack-dev \
    libstdc++6 libgomp1 \
    ca-certificates curl && \
    rm -rf /var/lib/apt/lists/*

# Create dedicated virtualenv
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1

# Install Python packages into *this* venv only
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir \
      "numpy==1.26.4" \
      "qiskit==1.1.1" \
      "qiskit-aer==0.14.2" \
      "qiskit-algorithms==0.2.2" \
      "qiskit-ibm-runtime==0.24.0" \
      "tweedledum" \
      "python-dotenv" && \
    python - <<'PY'
import qiskit, qiskit_aer, qiskit_algorithms
print("QISKIT", qiskit.__version__)
print("AER", qiskit_aer.__version__)
print("ALG", qiskit_algorithms.__version__)
PY

# App files
WORKDIR /app
COPY --from=builder /build/web/target/plank-db.jar ./plank-db.jar
COPY python/ ./python/

# Point app to the exact interpreter; expose scripts via PYTHONPATH
ENV PYTHONPATH=/app/python \
    QUANTUM_PYTHON_EXEC=/opt/venv/bin/python \
    SPRING_PROFILES_ACTIVE=default

# (Optional) more verbose logs for the adapter package
ENV LOGGING_LEVEL_io_github_swampus_quantum_explain_adapter=TRACE

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "plank-db.jar"]

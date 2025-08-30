# === Stage 1: Build the JAR using Maven ===
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

COPY . .
RUN mvn clean package -DskipTests

# === Stage 2: Runtime with Java + Python ===
FROM openjdk:17-slim
WORKDIR /app

# System deps for Qiskit/Aer
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip python3-venv \
    build-essential gcc g++ gfortran \
    libopenblas-dev liblapack-dev libomp-dev libgfortran5 \
    ca-certificates apt-transport-https \
  && rm -rf /var/lib/apt/lists/*

# Python venv
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1

# Python deps (pinned)
RUN pip install --no-cache-dir --upgrade pip \
 && pip install --no-cache-dir \
    "qiskit~=1.0" \
    "qiskit-aer" \
    "qiskit-algorithms" \
    "qiskit-ibm-runtime>=0.24.0" \
    "tweedledum" \
    "python-dotenv" \
    "numpy==1.26.4"

# Copy artifacts
COPY --from=builder /build/web/target/plank-db.jar ./plank-db.jar
COPY python/ ./python/

ENV PYTHONPATH=/app/python
ENV SPRING_PROFILES_ACTIVE=default

EXPOSE 8085

# HEALTHCHECK
HEALTHCHECK --interval=30s --timeout=3s --retries=5 CMD \
  curl -fsS http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "plank-db.jar"]

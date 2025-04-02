FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Установим Python и pip
RUN apk add --no-cache python3 py3-pip build-base && \
    pip install qiskit qiskit-ibm-runtime

# Копируем исходники и Python-скрипты
COPY ./infrastructure/target/infrastructure-*.jar ./app.jar
COPY ./python ./python

# Переменные окружения
ENV QUANTUM_MODE=LOCAL
ENV QUANTUM_PYTHON_EXEC=python3

# Запуск
ENTRYPOINT ["java", "-jar", "app.jar"]
# Default values (you can override them in .env)
export QUANTUM_EXECUTION_MODE=IBM
export QUANTUM_PYTHON_EXEC=/usr/bin/python3
export QUANTUM_IBM_SCRIPT_PATH=/app/python/grover_ibm.py
export QUANTUM_IBM_RANGE_SCRIPT_PATH=/app/python/grover_range_ibm.py
export QUANTUM_LOCAL_SCRIPT_PATH=/app/python/grover_local.py
export QUANTUM_LOCAL_RANGE_SCRIPT_PATH=/app/python/grover_range_local.py
export QUANTUM_IBM_TOKEN=REPLACE_WITH_YOUR_IBM_TOKEN

# Build the JAR
build:
	mvn clean package -DskipTests

# Build Docker image
docker-build: build
	docker compose build

# Run Docker container
docker-up:
	docker compose up

# Stop container
docker-down:
	docker compose down

# Rebuild and restart
restart: docker-down docker-build docker-up

# Show logs
logs:
	docker compose logs -f

# Clean everything
clean:
	docker compose down -v
	mvn clean

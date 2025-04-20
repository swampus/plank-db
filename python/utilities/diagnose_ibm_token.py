import os
from dotenv import load_dotenv
from qiskit_ibm_runtime import QiskitRuntimeService

load_dotenv(dotenv_path="/app/.env")

token = os.getenv("QUANTUM_IBM_TOKEN")
if not token:
    print("❌ QUANTUM_IBM_TOKEN is not set in /app/.env")
    exit(1)

try:
    service = QiskitRuntimeService(
        token=token,
        channel="ibm_cloud",
        instance="ibm-q/open/main"
    )

    print("✅ Token is valid. Available backends:")
    for backend in service.backends():
        print(" -", backend.name)

except Exception as e:
    print("❌ Token validation failed:")
    print(str(e))

import os
from dotenv import load_dotenv
from qiskit_ibm_runtime import QiskitRuntimeService

def main():
    load_dotenv()
    token = os.getenv("QUANTUM_IBM_TOKEN")
    if not token:
        print("❌ QUANTUM_IBM_TOKEN not set. Add it to .env or pass it via environment variable.")
        return

    print("🔐 Connecting to IBM Quantum...")
    service = QiskitRuntimeService(channel="ibm_quantum", token=token)

    print("\n🔍 Available IBM Quantum backends:")
    for backend in service.backends():
        name = backend.name
        description = backend.configuration().description
        is_sim = backend.configuration().simulator
        sim_tag = "[SIM]" if is_sim else "[REAL]"
        print(f" - {name} {sim_tag} :: {description}")

if __name__ == "__main__":
    main()

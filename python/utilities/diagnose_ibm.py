import os
from dotenv import load_dotenv
from qiskit_ibm_runtime import QiskitRuntimeService
from qiskit.providers.exceptions import QiskitBackendNotFoundError

def main():
    print("🔍 IBM Quantum Diagnostic Tool\n")

    load_dotenv()
    token = os.getenv("QUANTUM_IBM_TOKEN")

    if not token:
        print("❌ QUANTUM_IBM_TOKEN not found in environment or .env file.")
        return

    try:
        print("🔐 Connecting to IBM Quantum...")
        service = QiskitRuntimeService(channel="ibm_quantum", token=token)
        print("✅ Connection successful.\n")

        print("📡 Fetching available backends...")
        try:
            backends = service.backends()
        except QiskitBackendNotFoundError:
            print("❌ No backend matches the criteria. Possibly no access.")
            return
        except Exception as e:
            print(f"❌ Failed to fetch backends: {e}")
            return

        if not backends:
            print("❌ No backends returned.")
            return

        print(f"✅ {len(backends)} backends available:\n")
        for b in backends:
            sim_flag = "[SIM]" if b.configuration().simulator else "[REAL]"
            print(f" - {b.name:25} {sim_flag} | {b.configuration().description}")

        backend_names = [b.name for b in backends]
        if "ibmq_qasm_simulator" in backend_names:
            print("\n✅ ibmq_qasm_simulator is available and ready.")
        else:
            print("\n⚠️  ibmq_qasm_simulator is NOT available to this account.")

    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")

if __name__ == "__main__":
    main()

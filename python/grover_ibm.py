import sys
import json
import traceback

from qiskit_ibm_runtime import QiskitRuntimeService, Sampler
from qiskit import QuantumCircuit
from qiskit.circuit.library import ZGate
from qiskit_algorithms import Grover, AmplificationProblem

try:
    if len(sys.argv) < 4:
        print("NOT_FOUND", flush=True)
        sys.exit(1)

    # --- Parse input arguments ---
    token = sys.argv[1]
    search_key = sys.argv[2]
    keys_json = sys.argv[3]

    keys = json.loads(keys_json)

    # --- Authenticate with IBM Quantum ---
    service = QiskitRuntimeService(channel="ibm_quantum", token=token)

    backend = service.backend("ibmq_qasm_simulator")  # can be changed to a real backend
    sampler = Sampler(backend=backend)

    # --- Determine number of qubits ---
    num_qubits = len(keys).bit_length()

    # --- Build key -> index mapping ---
    key_index = {key: i for i, key in enumerate(keys)}
    target_index = key_index.get(search_key, None)

    if target_index is None:
        print("NOT_FOUND", flush=True)
        sys.exit(0)

    # --- Create Grover oracle circuit ---
    oracle = QuantumCircuit(num_qubits)
    binary = format(target_index, f'0{num_qubits}b')

    for i, bit in enumerate(reversed(binary)):
        if bit == '0':
            oracle.x(i)

    oracle.append(ZGate(), [i for i in range(num_qubits)])

    for i, bit in enumerate(reversed(binary)):
        if bit == '0':
            oracle.x(i)

    oracle_gate = oracle.to_gate(label="Oracle")

    # --- Create the problem and run Grover ---
    problem = AmplificationProblem(oracle_gate)
    grover = Grover(sampler=sampler)

    result = grover.amplify(problem)

    # --- Extract top result ---
    top_measurement = max(result.assignment_counts, key=result.assignment_counts.get)
    found_index = int(top_measurement, 2)

    found_key = keys[found_index] if found_index < len(keys) else "NOT_FOUND"

    print(found_key, flush=True)

except Exception:
    traceback.print_exc()
    print("NOT_FOUND", flush=True)
    sys.exit(1)

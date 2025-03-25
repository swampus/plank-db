import sys
import json
from qiskit import BasicAer, QuantumCircuit
from qiskit.algorithms import Grover, AmplificationProblem

def encode_items(items):
    bit_len = (len(items) - 1).bit_length()
    encoding = {item: format(i, f'0{bit_len}b') for i, item in enumerate(items)}
    decoding = {v: k for k, v in encoding.items()}
    return encoding, decoding, bit_len

def build_oracle(target_binary):
    n = len(target_binary)
    qc = QuantumCircuit(n, name="Oracle")
    input_qubits = list(range(n - 1))
    target_qubit = n - 1

    for i, bit in enumerate(reversed(target_binary)):
        if bit == '0':
            qc.x(i)
    qc.h(target_qubit)
    qc.mct(input_qubits, target_qubit)
    qc.h(target_qubit)
    for i, bit in enumerate(reversed(target_binary)):
        if bit == '0':
            qc.x(i)

    return qc

def main():
    try:
        if len(sys.argv) != 3:
            print("NOT_FOUND")
            return

        target_key = sys.argv[1]
        try:
            items = json.loads(sys.argv[2])
        except json.JSONDecodeError:
            print("NOT_FOUND")
            return

        if not isinstance(items, list) or target_key not in items:
            print("NOT_FOUND")
            return

        encoding, decoding, n_bits = encode_items(items)
        target_binary = encoding[target_key]
        oracle = build_oracle(target_binary)

        backend = BasicAer.get_backend('qasm_simulator')
        grover = Grover(quantum_instance=backend)
        problem = AmplificationProblem(oracle)
        result = grover.amplify(problem)

        top = max(result.circuit_results.items(), key=lambda x: x[1])[0]
        print(decoding.get(top, "NOT_FOUND"))

    except Exception as e:
        print("ERROR:", str(e))
        sys.exit(1)

if __name__ == "__main__":
    main()
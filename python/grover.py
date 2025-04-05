import sys
import json
from qiskit import BasicAer
from qiskit.algorithms import Grover, AmplificationProblem
from qiskit.circuit.library import PhaseOracle

# Exit codes
EXIT_SUCCESS = 0
EXIT_COLLECTION_NOT_FOUND = 2
EXIT_INVALID_INPUT = 3
EXIT_INTERNAL_ERROR = 4


def encode_items(items):
    encoding = {}
    decoding = {}
    for i, item in enumerate(items):
        binary = format(i, f"0{(len(items)-1).bit_length()}b")
        encoding[item] = binary
        decoding[binary] = item
    return encoding, decoding, (len(items)-1).bit_length()


def build_oracle(target_binary):
    return PhaseOracle(f"(x{target_binary})")


def main():
    try:
        if len(sys.argv) != 3:
            print("Usage: grover.py <key> <json>", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        target_key = sys.argv[1]

        try:
            items = json.loads(sys.argv[2])
        except json.JSONDecodeError:
            print("Invalid JSON format", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        if not isinstance(items, list):
            print("Expected a list of items", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        if target_key not in items:
            print("CollectionNotFound", file=sys.stderr)
            sys.exit(EXIT_COLLECTION_NOT_FOUND)

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
        print(f"Unexpected error: {str(e)}", file=sys.stderr)
        sys.exit(EXIT_INTERNAL_ERROR)


if __name__ == "__main__":
    main()

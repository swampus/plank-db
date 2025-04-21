import sys
import json
import logging
import time
import argparse
from math import floor, pi, sqrt
from collections import Counter
import numpy as np
from qiskit.circuit.library import PhaseOracle
from qiskit_algorithms import Grover, AmplificationProblem
from qiskit_aer.primitives import Sampler

# 🧿 Oratio ad Algorithmos Quanticos
# Domine Qubitorum, lucem superpositionis nobis praebe.
# Algorithmus fiat, et numeri se inveniant.
# Ne errorum fluctus circuitum confundant,
# sed amplitudo vera emergat.
# Fiat Grover. Fiat Lux. 🧿

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

EXIT_SUCCESS = 0
EXIT_COLLECTION_NOT_FOUND = 2
EXIT_INVALID_INPUT = 3
EXIT_INTERNAL_ERROR = 4

def encode_items(items):
    logging.info("Encoding items into binary.")
    encoding = {}
    decoding = {}
    n_bits = (len(items) - 1).bit_length()
    for i, item in enumerate(items):
        binary = format(i, f"0{n_bits}b")
        encoding[item] = binary
        decoding[binary] = item
        logging.info(f"Encoded '{item}' as '{binary}'")
    return encoding, decoding, n_bits

def build_oracle(target_binary):
    logging.info(f"Creating oracle for binary: {target_binary}")
    return PhaseOracle(f"(x{target_binary})")

def main():
    try:
        start_time = time.time()
        logging.info("=== Grover Local Search Start ===")

        parser = argparse.ArgumentParser()
        parser.add_argument("target_key")
        parser.add_argument("keys_json")
        parser.add_argument("entries_json")
        parser.add_argument("--iterations", type=int, default=-1)
        parser.add_argument("--backend", type=str, choices=["local", "ibm"], default="local")
        args = parser.parse_args()

        target_key = args.target_key
        try:
            keys = json.loads(args.keys_json)
            entries = json.loads(args.entries_json)
        except json.JSONDecodeError:
            print("Invalid JSON input", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        if target_key not in keys or target_key not in entries:
            print("CollectionNotFound", file=sys.stderr)
            sys.exit(EXIT_COLLECTION_NOT_FOUND)

        encoding, decoding, n_bits = encode_items(keys)
        target_binary = encoding[target_key]
        oracle = build_oracle(target_binary)

        np.random.seed(123)

        # ✅ Backend selection
        if args.backend == "local":
            sampler = Sampler()
        else:
            print("Only 'local' backend is supported in this version.", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        n_items = len(keys)
        optimal_iterations = floor((pi / 4) * sqrt(n_items))
        iterations = args.iterations if args.iterations > 0 else max(1, optimal_iterations)

        logging.info(f"Target key: {target_key}")
        logging.info(f"Target binary: {target_binary}")
        logging.info(f"Optimal iterations: {optimal_iterations}, used: {iterations}")
        logging.info(f"Number of qubits: {n_bits}")
        logging.info(f"Encoding map: {encoding}")

        grover = Grover(sampler=sampler, iterations=iterations)
        problem = AmplificationProblem(oracle)
        result = grover.amplify(problem)

        elapsed_ms = round((time.time() - start_time) * 1000)
        raw_top = result.top_measurement
        top = raw_top.zfill(n_bits)

        counts = result.circuit_results[0]
        total = sum(counts.values())
        probabilities = {k.zfill(n_bits): round(v / total, 4) for k, v in counts.items()}
        confidence = round(probabilities.get(top, 0.0), 4)

        matched_key = decoding.get(top)
        matched_value = entries.get(matched_key) if matched_key else None
        matched_index = list(keys).index(matched_key) if matched_key else None

        note = "Success" if confidence >= 0.6 else "Low confidence"
        if not matched_key:
            note = "No match found"

        output = {
            "quantum_result": {
                "matched_key": matched_key,
                "matched_value": matched_value,
                "matched_index": matched_index,
                "top_measurement": top,
                "oracle_expression": f"(x{target_binary})",
                "num_qubits": n_bits,
                "probabilities": probabilities,
                "confidence_score": confidence,
                "execution_time_ms": elapsed_ms,
                "oracle_depth": oracle.decompose().depth(),
                "iterations": iterations,
                "note": note
            },
            "scientific_notes": {
                "principle": "Grover's algorithm enables quadratic speedup for unstructured search problems.",
                "theory": (
                    "Grover's search uses quantum amplitude amplification to find a target entry among N possibilities "
                    "in approximately √N steps, instead of O(N) classically. This provides a quadratic speedup."
                ),
                "circuit_behavior": (
                    "All states start in superposition. The oracle inverts the phase of the target state. "
                    "The diffusion operator amplifies the probability of measuring the correct state."
                ),
                "confidence_interpretation": (
                    "A confidence score < 0.6 may indicate insufficient iterations or small problem size "
                    "where quantum advantage is not fully visible. You can increase 'iterations' for better amplification."
                ),
                "qubit_commentary": (
                    f"This search was performed using {n_bits} qubit(s), corresponding to {2**n_bits} possible states."
                ),
                "encoding_map": encoding,
                "used_iterations": iterations
            }
        }

        print(json.dumps(output, indent=2))
        logging.info("=== Grover Local Search Finished ===")
        sys.exit(EXIT_SUCCESS)

    except Exception as e:
        logging.exception("Unexpected error occurred")
        print(f"Unexpected error: {str(e)}", file=sys.stderr)
        sys.exit(EXIT_INTERNAL_ERROR)

if __name__ == "__main__":
    main()

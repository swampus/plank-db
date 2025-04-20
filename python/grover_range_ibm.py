import sys
import json
import logging
import time
import argparse
import os
from math import floor, pi, sqrt
from dotenv import load_dotenv

from qiskit import QuantumCircuit
from qiskit_aer.primitives import Sampler as LocalSampler
from qiskit_ibm_runtime import QiskitRuntimeService, Sampler, Session
from qiskit_algorithms import Grover, AmplificationProblem

logging.basicConfig(
    filename='grover_range.log',
    filemode='a',
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

EXIT_SUCCESS = 0
EXIT_COLLECTION_NOT_FOUND = 2
EXIT_INVALID_INPUT = 3
EXIT_INTERNAL_ERROR = 4

def encode_items(items):
    encoding = {}
    decoding = {}
    n_bits = (len(items) - 1).bit_length()
    for i, item in enumerate(items):
        binary = format(i, f"0{n_bits}b")
        encoding[item] = binary
        decoding[binary] = item
    return encoding, decoding, n_bits

def build_oracle(filtered_keys, encoding):
    n = len(next(iter(encoding.values())))
    qc = QuantumCircuit(n)
    oracle_expr = []

    for key in filtered_keys:
        bin_key = encoding[key]
        expr = []
        for i, bit in enumerate(bin_key):
            if bit == "0":
                qc.x(i)
        qc.h(n - 1)
        qc.mcx(list(range(n - 1)), n - 1)
        qc.h(n - 1)
        for i, bit in enumerate(bin_key):
            if bit == "0":
                qc.x(i)
        expr.append(f"x{bin_key}")
        oracle_expr.append("".join(expr))

    qc.name = f"oracle_range({','.join(oracle_expr)})"
    return qc, " | ".join(oracle_expr)

def main():
    try:
        start_time = time.time()

        parser = argparse.ArgumentParser()
        parser.add_argument("min_key")
        parser.add_argument("max_key")
        parser.add_argument("keys_json")
        parser.add_argument("--iterations", type=int, default=-1)
        parser.add_argument("--backend", choices=["local", "ibm"], default="local")
        parser.add_argument("--ibm-backend", default="ibmq_qasm_simulator")
        args = parser.parse_args()

        try:
            keys = json.loads(args.keys_json)
        except json.JSONDecodeError:
            print("Invalid JSON input", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        if not isinstance(keys, list) or not keys:
            print("Expected a non-empty list of keys", file=sys.stderr)
            sys.exit(EXIT_COLLECTION_NOT_FOUND)

        filtered_keys = [k for k in keys if args.min_key <= k <= args.max_key]
        encoding, decoding, n_bits = encode_items(keys)
        if not filtered_keys:
            print("No keys matched the range", file=sys.stderr)
            sys.exit(EXIT_COLLECTION_NOT_FOUND)

        oracle, oracle_expr = build_oracle(filtered_keys, encoding)

        optimal_iterations = floor((pi / 4) * sqrt(len(filtered_keys)))
        iterations = args.iterations if args.iterations > 0 else max(1, optimal_iterations)

        result = None
        if args.backend == "local":
            sampler = LocalSampler()
            grover = Grover(sampler=sampler, iterations=iterations)
            problem = AmplificationProblem(oracle)
            result = grover.amplify(problem)

        elif args.backend == "ibm":
            load_dotenv()
            token = os.getenv("QUANTUM_IBM_TOKEN")
            if not token:
                raise RuntimeError("QUANTUM_IBM_TOKEN is not set in environment or .env")

            service = QiskitRuntimeService(token=token, channel="ibm_cloud", instance="ibm-q/open/main")
            backend_obj = service.backend(args.ibm_backend)

            with Session(backend=backend_obj) as session:
                target = session.target
                transpiled_oracle = target.transpile(oracle)
                sampler = Sampler(session=session)
                grover = Grover(sampler=sampler, iterations=iterations)
                problem = AmplificationProblem(transpiled_oracle)
                result = grover.amplify(problem)

        elapsed_ms = round((time.time() - start_time) * 1000)
        raw_top = result.top_measurement
        top = raw_top.zfill(n_bits)

        counts = result.circuit_results[0]
        total = sum(counts.values())
        probabilities = {k.zfill(n_bits): round(v / total, 4) for k, v in counts.items()}
        confidence = round(probabilities.get(top, 0.0), 4)

        matched_key = decoding.get(top)
        note = "Success" if confidence >= 0.6 else "Low confidence"
        if not matched_key:
            note = "No match found"

        confidence_interpretation = (
            "No confident match found. Increase iterations or review key distribution."
            if confidence < 0.6 else
            "Confidence is high. Match likely correct."
        )

        qubit_commentary = (
            f"Used {n_bits} qubit(s) ({2**n_bits} possible states)."
            + (" Match not observed." if not matched_key else f" Observed match for '{matched_key}'.")
        )

        output = {
            "quantum_result": {
                "matched_key": matched_key,
                "top_measurement": top,
                "oracle_expression": oracle_expr,
                "num_qubits": n_bits,
                "probabilities": probabilities,
                "confidence_score": confidence,
                "execution_time_ms": elapsed_ms,
                "oracle_depth": oracle.depth(),
                "iterations": iterations,
                "matched_value": None,
                "matched_index": None,
                "note": note
            },
            "scientific_notes": {
                "principle": "Grover's algorithm enables quadratic speedup for unstructured search problems.",
                "theory": (
                    "Grover's algorithm is particularly powerful when the solution lies within a known subset (range). "
                    "By building an oracle over a filtered set, we apply quantum amplitude amplification only where needed."
                ),
                "circuit_behavior": (
                    "The oracle flips the phase of all matching states in the range. "
                    "The diffusion operator amplifies their probability amplitude."
                ),
                "confidence_interpretation": confidence_interpretation,
                "qubit_commentary": qubit_commentary,
                "encoding_map": encoding,
                "used_iterations": iterations,
                "range_bounds": [args.min_key, args.max_key]
            }
        }

        print(json.dumps(output, indent=2))
        sys.exit(EXIT_SUCCESS)

    except Exception as e:
        logging.exception("Unexpected error occurred")
        print(f"Unexpected error: {str(e)}", file=sys.stderr)
        sys.exit(EXIT_INTERNAL_ERROR)

if __name__ == "__main__":
    main()

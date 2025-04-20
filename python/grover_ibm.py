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
from qiskit_algorithms import Grover, AmplificationProblem
from qiskit_ibm_runtime import QiskitRuntimeService, Sampler, Session

logging.basicConfig(
    filename='grover.log',
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

def build_oracle(target_binary):
    n = len(target_binary)
    qc = QuantumCircuit(n)
    for i, bit in enumerate(target_binary):
        if bit == "0":
            qc.x(i)
    qc.h(n - 1)
    qc.mcx(list(range(n - 1)), n - 1)
    qc.h(n - 1)
    for i, bit in enumerate(target_binary):
        if bit == "0":
            qc.x(i)
    qc.name = f"oracle({target_binary})"
    return qc

def main():
    try:
        start_time = time.time()

        parser = argparse.ArgumentParser()
        parser.add_argument("target_key")
        parser.add_argument("keys_json")
        parser.add_argument("entries_json")
        parser.add_argument("--iterations", type=int, default=-1)
        parser.add_argument("--backend", choices=["local", "ibm"], default="local")
        parser.add_argument("--ibm-backend", default="ibmq_qasm_simulator")
        args = parser.parse_args()

        try:
            keys = json.loads(args.keys_json)
            entries = json.loads(args.entries_json)
        except json.JSONDecodeError:
            print("Invalid JSON input", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        if args.target_key not in keys or args.target_key not in entries:
            print("CollectionNotFound", file=sys.stderr)
            sys.exit(EXIT_COLLECTION_NOT_FOUND)

        encoding, decoding, n_bits = encode_items(keys)
        if n_bits == 0:
            print("Too few keys for Grover search. Must be at least 2 keys (1 qubit).", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)

        target_binary = encoding[args.target_key]
        oracle = build_oracle(target_binary)

        n_items = len(keys)
        optimal_iterations = floor((pi / 4) * sqrt(n_items))
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

            # Надёжно передаём токен и канал явно
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
                "oracle_depth": oracle.depth(),
                "iterations": iterations,
                "note": note
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
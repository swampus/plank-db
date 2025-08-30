#!/usr/bin/env python3
"""
Grover 'Explain' Dry-Run

This script builds a minimal Grover circuit for a given set of marked bitstrings
and executes it on the local Aer simulator to produce a probability distribution.
It prints a single JSON object to STDOUT on success.

Intended usage: called from the Java adapter (QuantumProcessRunner) as:
  python3 /app/python/explain.py --qubits 3 --states 101,011 --iterations 2 --shots 2048 --seed 42

Exit codes (so the Java runner can map them):
  0  - success (JSON on STDOUT)
  3  - invalid input (prints an error message to STDERR)
  4  - backend/external error (prints an error message to STDERR)
"""

import argparse
import json
import sys
import time
from typing import List

from qiskit import QuantumCircuit, transpile
from qiskit_aer import Aer


# -----------------------
# Oracle / Diffuser utils
# -----------------------
def phase_flip_for_state(qc: QuantumCircuit, bitstr: str) -> None:
    """
    Implements a phase oracle that flips the phase of |bitstr>.
    Convention: the string's leftmost char is the MSB; we apply gates on qubits
    using little-endian indexing (Qiskit convention: qubit 0 is the least significant).
    To match a '1', we do nothing; to match a '0', we wrap with X so MCZ triggers only on the exact state.

    For n>1: we implement multi-controlled Z via H on the last qubit + MCX + H.
    """
    n = len(bitstr)
    # Positions that are '0' in the target state => we add X before and after
    zero_pos = [i for i, b in enumerate(reversed(bitstr)) if b == "0"]  # reverse => little-endian mapping
    for q in zero_pos:
        qc.x(q)

    if n == 1:
        qc.z(0)
    else:
        qc.h(n - 1)
        qc.mcx(list(range(n - 1)), n - 1)
        qc.h(n - 1)

    for q in zero_pos:
        qc.x(q)


def diffuser(qc: QuantumCircuit, n: int) -> None:
    """
    Standard Grover diffuser:
      H^n • X^n • (multi-controlled Z) • X^n • H^n
    """
    for q in range(n):
        qc.h(q)
        qc.x(q)

    if n == 1:
        qc.z(0)
    else:
        qc.h(n - 1)
        qc.mcx(list(range(n - 1)), n - 1)
        qc.h(n - 1)

    for q in range(n):
        qc.x(q)
        qc.h(q)


def build_grover_circuit(n: int, marked_states: List[str], iterations: int) -> QuantumCircuit:
    """
    Build a minimal Grover circuit:
      - Prepare uniform superposition with H on all qubits
      - Repeat { Oracle; Diffuser } for the requested number of iterations
      - Measure all qubits to classical bits (same indices)
    """
    qc = QuantumCircuit(n, n)
    for q in range(n):
        qc.h(q)

    for _ in range(iterations):
        for s in marked_states:
            phase_flip_for_state(qc, s)
        diffuser(qc, n)

    qc.measure(range(n), range(n))
    return qc


# -----------------------
# Validation helpers
# -----------------------
def validate_args(n: int, states_raw: str, iterations: int, shots: int) -> List[str]:
    """
    Validate CLI arguments and return a cleaned list of marked states.
    Raises ValueError on invalid input so we can exit with code 3.
    """
    if n <= 0:
        raise ValueError("--qubits must be >= 1")
    if iterations <= 0:
        raise ValueError("--iterations must be >= 1")
    if shots <= 0:
        raise ValueError("--shots must be >= 1")

    marked = [s.strip() for s in states_raw.split(",") if s.strip()]
    if not marked:
        raise ValueError("--states must contain at least one bitstring (e.g. '01,10')")

    for s in marked:
        if len(s) != n:
            raise ValueError(f"all states must have length == --qubits ({n}), got '{s}'")
        if any(c not in ("0", "1") for c in s):
            raise ValueError(f"state '{s}' contains non-binary characters")

    return marked


# -----------------------
# Main entry point
# -----------------------
def main() -> None:
    parser = argparse.ArgumentParser(description="Grover dry-run on Aer simulator (Explain endpoint helper)")
    parser.add_argument("--qubits", type=int, required=True, help="Number of qubits (>=1)")
    parser.add_argument("--states", type=str, required=True, help="Comma-separated marked states, e.g. '01,10'")
    parser.add_argument("--iterations", type=int, required=True, help="Number of Grover iterations (>=1)")
    parser.add_argument("--shots", type=int, default=2048, help="Number of shots for simulation (>=1)")
    parser.add_argument("--seed", type=int, default=None, help="Optional seed for reproducibility")

    args = parser.parse_args()

    try:
        # 1) Validate and normalize inputs
        marked = validate_args(args.qubits, args.states, args.iterations, args.shots)

        # 2) Build circuit
        qc = build_grover_circuit(args.qubits, marked, args.iterations)

        # 3) Run on Aer simulator (Qiskit 1.x)
        backend = Aer.get_backend("aer_simulator")
        if args.seed is not None:
            backend.set_options(seed_simulator=args.seed)

        tqc = transpile(qc, backend)
        start = time.time()
        result = backend.run(tqc, shots=args.shots).result()
        counts = result.get_counts()
        exec_ms = int((time.time() - start) * 1000)

        # 4) Convert counts -> probabilities + top measurement
        total = sum(counts.values()) or 1
        probabilities = {str(k): v / total for k, v in counts.items()}
        top = max(probabilities, key=probabilities.get) if probabilities else None
        confidence = probabilities.get(top, 0.0) if top else 0.0

        # 5) Emit JSON to STDOUT (single-line)
        print(json.dumps({
            "top_measurement": top,
            "probabilities": probabilities,
            "confidence": confidence,
            "execution_time_ms": exec_ms
        }))
        sys.exit(0)

    except ValueError as ve:
        # Invalid input => exit code 3 so Java maps to "invalid input"
        sys.stderr.write(f"[invalid] {ve}\n")
        sys.exit(3)
    except Exception as e:
        # Anything else is treated as backend/external error => exit code 4
        sys.stderr.write(f"[backend] {e}\n")
        sys.exit(4)


if __name__ == "__main__":
    main()
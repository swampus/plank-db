#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Grover 'Explain' Dry-Run (Aer, Qiskit 1.x)

This script builds a minimal Grover circuit for a given set of marked bitstrings
(phase oracle + diffuser) and executes it on the local Aer simulator to produce
a probability distribution. It prints a single JSON object to STDOUT on success.

Typical usage (from the Java adapter, e.g., QuantumProcessRunner):
  python3 /app/python/explain.py \
      --qubits 3 \
      --states 101,011 \
      --iterations 2 \
      --shots 2048 \
      --seed 42

Exit codes (so the Java runner can map them):
  0  - success (prints JSON on STDOUT)
  3  - invalid input (prints a user-facing error message to STDERR)
  4  - backend/external error (prints a technical error message to STDERR)

Notes:
- Endianness: This script assumes bitstrings are given MSB→LSB (left→right).
  Qiskit qubits are indexed little-endian (qubit 0 is LSB). The oracle builder
  accounts for that by reversing positions internally when placing X gates.
- The goal here is *explainability*, not a full Grover implementation:
  we implement a lightweight phase-flip oracle and the standard diffuser exactly
  as used in many educational references; it is sufficient for creating a dry run.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from typing import List

from qiskit.circuit import QuantumCircuit
from qiskit_aer import Aer
from qiskit import transpile


# -----------------------
# Exit code constants
# -----------------------
EXIT_SUCCESS = 0
EXIT_INVALID_INPUT = 3
EXIT_BACKEND_ERROR = 4


# -----------------------
# Oracle / Diffuser utils
# -----------------------
def phase_flip_for_state(qc: QuantumCircuit, bitstr: str) -> None:
    """
    Apply a phase oracle that flips the phase of |bitstr>.

    Conventions:
      - `bitstr` is read as MSB→LSB (left→right), e.g., "101" means
        MSB=1, next=0, LSB=1.
      - Qiskit uses little-endian qubit indexing (qubit 0 is LSB).
        Therefore, we reverse `bitstr` when mapping to physical qubit indices.
      - To mark a target state, we:
          * X all qubits where the target bit is '0' (so the MCZ triggers only on the exact state),
          * apply a multi-controlled Z,
          * uncompute the X gates.

    For n == 1, we simply apply Z on the single qubit.
    For n > 1, we synthesize MCZ via H on the last qubit + MCX + H.
    """
    n = len(bitstr)
    # Identify positions that are '0' in the target state (reverse for LE→phys map).
    zero_positions_le = [i for i, b in enumerate(reversed(bitstr)) if b == "0"]

    # Compute: flip zeros so the state matches |11..1> under MCZ
    for q in zero_positions_le:
        qc.x(q)

    # Multi-controlled Z
    if n == 1:
        qc.z(0)
    else:
        qc.h(n - 1)
        qc.mcx(list(range(n - 1)), n - 1)
        qc.h(n - 1)

    # Uncompute: restore original basis
    for q in zero_positions_le:
        qc.x(q)


def diffuser(qc: QuantumCircuit, n: int) -> None:
    """
    Standard Grover diffuser:

      H^n • X^n • MCZ • X^n • H^n

    Implementation detail:
    - For n == 1, MCZ becomes just Z on that qubit.
    - For n > 1, MCZ is synthesized via H + MCX + H on the last qubit.
    """
    # Prepare |+>^n then flip all to set up the MCZ around |11..1>
    for q in range(n):
        qc.h(q)
        qc.x(q)

    if n == 1:
        qc.z(0)
    else:
        qc.h(n - 1)
        qc.mcx(list(range(n - 1)), n - 1)
        qc.h(n - 1)

    # Uncompute the X and H layers
    for q in range(n):
        qc.x(q)
        qc.h(q)


def build_grover_circuit(n: int, marked_states: List[str], iterations: int) -> QuantumCircuit:
    """
    Build a minimal Grover circuit for `n` qubits and the provided marked states:

      1) Initialize to uniform superposition via H on all qubits.
      2) Repeat `iterations` times:
           - Apply the phase oracle for each marked state
           - Apply the diffuser
      3) Measure all qubits to classical bits (one-to-one mapping).

    This matches the typical textbook Grover layout well enough for a dry run.
    """
    qc = QuantumCircuit(n, n)

    # Step 1: Uniform superposition
    for q in range(n):
        qc.h(q)

    # Step 2: Grover iterations
    for _ in range(iterations):
        for s in marked_states:
            phase_flip_for_state(qc, s)
        diffuser(qc, n)

    # Step 3: Measure
    qc.measure(range(n), range(n))
    return qc


# -----------------------
# Validation helpers
# -----------------------
def validate_args(n: int, states_raw: str, iterations: int, shots: int) -> List[str]:
    """
    Validate CLI arguments and return a cleaned list of marked states.

    Raises:
      ValueError: if any argument is invalid

    Validation rules:
      - n >= 1
      - iterations >= 1
      - shots >= 1
      - states: comma-separated bitstrings, each of length n and [0/1]-only
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
    parser = argparse.ArgumentParser(
        description="Grover dry-run on Aer simulator (Explain endpoint helper)"
    )
    parser.add_argument("--qubits", type=int, required=True, help="Number of qubits (>=1)")
    parser.add_argument(
        "--states",
        type=str,
        required=True,
        help="Comma-separated marked states, e.g. '01,10' (MSB→LSB)",
    )
    parser.add_argument("--iterations", type=int, required=True, help="Grover iterations (>=1)")
    parser.add_argument("--shots", type=int, default=2048, help="Number of shots (>=1)")
    parser.add_argument("--seed", type=int, default=None, help="Optional seed for reproducibility")

    args = parser.parse_args()

    try:
        # 1) Validate & normalize inputs
        marked = validate_args(args.qubits, args.states, args.iterations, args.shots)

        # 2) Build circuit
        qc = build_grover_circuit(args.qubits, marked, args.iterations)

        # 3) Run on Aer simulator (Qiskit 1.x)
        backend = Aer.get_backend("aer_simulator")
        if args.seed is not None:
            # Qiskit Aer 0.14+ supports seed via set_options
            backend.set_options(seed_simulator=args.seed)

        tqc = transpile(qc, backend)
        t0 = time.time()
        # Run and extract counts
        result = backend.run(tqc, shots=args.shots).result()
        counts = result.get_counts()
        exec_ms = int((time.time() - t0) * 1000)

        # 4) Convert counts → probabilities + top measurement
        total = sum(counts.values()) or 1
        # Keys in `counts` are bitstrings in the register measurement order
        probabilities = {str(k): v / total for k, v in counts.items()}
        top = max(probabilities, key=probabilities.get) if probabilities else None
        confidence = float(probabilities.get(top, 0.0)) if top else 0.0

        # 5) Emit a single-line JSON to STDOUT (contract for Java runner)
        print(
            json.dumps(
                {
                    "top_measurement": top,        # bitstring, as observed from measurements
                    "probabilities": probabilities, # map: bitstring -> probability in [0,1]
                    "confidence": confidence,       # probability mass of the top outcome
                    "execution_time_ms": exec_ms,   # wall-clock execution time for the dry run
                }
            )
        )
        sys.exit(EXIT_SUCCESS)

    except ValueError as ve:
        # Invalid input => exit code 3 so Java maps to "invalid input"
        sys.stderr.write(f"[invalid] {ve}\n")
        sys.exit(EXIT_INVALID_INPUT)

    except Exception as e:
        # Anything else is treated as backend/external error => exit code 4
        # Keep the message concise; the caller can log/stash STDERR if needed.
        sys.stderr.write(f"[backend] {e}\n")
        sys.exit(EXIT_BACKEND_ERROR)


if __name__ == "__main__":
    main()

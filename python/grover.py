#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Grover key search (LOCAL/Aer) using Qiskit 1.x primitives (Sampler).
- No QuantumInstance: uses qiskit_aer.primitives.Sampler
- Deterministic via --shots/--seed
- Returns rich JSON with matched entry, probabilities, top_k, and optional images (circuit/histogram)
"""

import sys
import json
import time
import base64
import argparse
import logging
from io import BytesIO
from math import floor, pi, sqrt
from typing import Dict, List, Tuple, Optional

import numpy as np

from qiskit.circuit import QuantumCircuit
from qiskit.circuit.library import PhaseOracle
from qiskit_algorithms import Grover, AmplificationProblem
from qiskit_aer.primitives import Sampler  # AerSampler in Qiskit 1.x
from qiskit.visualization import plot_histogram

# --- Logging (plain, container-friendly) ---
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)

# --- Exit codes (used by Java runner to classify errors) ---
EXIT_SUCCESS = 0
EXIT_COLLECTION_NOT_FOUND = 2
EXIT_INVALID_INPUT = 3
EXIT_INTERNAL_ERROR = 4


# ---------- helpers ----------

def encode_items(items: List[str]) -> Tuple[Dict[str, str], Dict[str, str], int]:
    """Assign contiguous binary codes to items (preserving input order)."""
    n_bits = max(1, (len(items) - 1).bit_length())
    encoding: Dict[str, str] = {}
    decoding: Dict[str, str] = {}
    for i, item in enumerate(items):
        b = format(i, f"0{n_bits}b")
        encoding[item] = b
        decoding[b] = item
    return encoding, decoding, n_bits


def build_oracle(target_binary: str) -> PhaseOracle:
    """Create a phase oracle that flips the phase of the target bitstring."""
    # Boolean expr format for PhaseOracle: x... means matching the literal bitstring
    return PhaseOracle(f"(x{target_binary})")


def diffuser(n: int) -> QuantumCircuit:
    """Standard Grover diffuser for n qubits (no ancillas)."""
    qc = QuantumCircuit(n, name="diffuser")
    qc.h(range(n))
    qc.x(range(n))
    # Z about |11...1> using H-MCX-H
    qc.h(n - 1)
    if n == 1:
        qc.z(0)
    else:
        qc.mcx(list(range(n - 1)), n - 1)
    qc.h(n - 1)
    qc.x(range(n))
    qc.h(range(n))
    return qc


def render_circuit_png(n_bits: int, oracle: PhaseOracle, iterations: int) -> Optional[str]:
    """Render a simple Grover circuit (H + oracle + k*diffuser) as base64 PNG.
    Note: This is an illustrative circuit; the algorithm's internal transpiled
    circuit can differ, but this is good enough for visualization."""
    try:
        qc = QuantumCircuit(n_bits, name="grover")
        # Initialize to equal superposition
        qc.h(range(n_bits))
        # One oracle application (the boolean oracle acts on the same n wires)
        qc.compose(oracle.to_circuit(), inplace=True)
        # k Grover iterations of the diffuser
        diff = diffuser(n_bits).to_instruction()
        for _ in range(max(0, iterations)):
            qc.append(diff, range(n_bits))
        # Draw with matplotlib backend
        buf = BytesIO()
        qc.draw(output="mpl")  # relies on matplotlib; set MPLCONFIGDIR=/tmp/mpl in container
        import matplotlib.pyplot as plt  # deferred import to avoid module load if unused
        plt.tight_layout()
        plt.savefig(buf, format="png", bbox_inches="tight")
        plt.close()
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception as e:
        logging.warning("Circuit render failed: %s", e)
        return None


def render_histogram_png(probabilities: Dict[str, float]) -> Optional[str]:
    """Render a probability histogram as base64 PNG."""
    try:
        buf = BytesIO()
        fig = plot_histogram(probabilities, figsize=(6, 3))  # uses matplotlib under the hood
        fig.savefig(buf, format="png", bbox_inches="tight")
        import matplotlib.pyplot as plt
        plt.close(fig)
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception as e:
        logging.warning("Histogram render failed: %s", e)
        return None


def depolarize_probs(probs: Dict[str, float], epsilon: float) -> Dict[str, float]:
    """Apply simple depolarizing noise on measurement distribution:
       p' = (1 - e) * p + e / 2^n."""
    if epsilon <= 0:
        return probs
    keys = list(probs.keys())
    n_bits = len(keys[0]) if keys else 0
    uniform = 1.0 / (2 ** n_bits) if n_bits > 0 else 0.0
    noisy = {k: (1.0 - epsilon) * v + epsilon * uniform for k, v in probs.items()}
    # Normalize to sum==1 just in case of numerical drift
    s = sum(noisy.values()) or 1.0
    return {k: v / s for k, v in noisy.items()}


# ---------- main ----------

def main():
    start_time = time.time()

    # CLI
    parser = argparse.ArgumentParser(description="Grover key search via Qiskit 1.x primitives (Sampler).")
    parser.add_argument("target_key")
    parser.add_argument("keys_json")
    parser.add_argument("entries_json")
    parser.add_argument("--iterations", type=int, default=-1, help="Number of Grover iterations; -1 = auto")
    parser.add_argument("--backend", type=str, choices=["local"], default="local", help="Only 'local' (Aer) is supported here")
    parser.add_argument("--shots", type=int, default=1024, help="Number of shots for Sampler")
    parser.add_argument("--seed", type=int, default=42, help="Seed for deterministic simulation")
    parser.add_argument("--topk", type=int, default=0, help="If >0, include top_k states with highest probabilities")
    parser.add_argument("--noise", type=float, default=0.0, help="0..1 depolarizing noise applied at measurement (synthetic)")
    parser.add_argument("--render", action="store_true", help="Include base64 images (circuit, histogram)")
    args = parser.parse_args()

    try:
        # Parse payloads
        keys = json.loads(args.keys_json)
        entries = json.loads(args.entries_json)
        if not isinstance(keys, list) or not isinstance(entries, dict):
            print("Invalid JSON shape: expected list for keys and dict for entries", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)
    except Exception:
        print("Invalid JSON input", file=sys.stderr)
        sys.exit(EXIT_INVALID_INPUT)

    # Basic checks
    if args.target_key not in keys or args.target_key not in entries:
        print("CollectionNotFound", file=sys.stderr)
        sys.exit(EXIT_COLLECTION_NOT_FOUND)

    # Encoding & oracle
    encoding, decoding, n_bits = encode_items(keys)
    target_binary = encoding[args.target_key]
    oracle = build_oracle(target_binary)

    # Iterations: AUTO ≈ floor(pi/4 * sqrt(N))
    n_items = len(keys)
    auto_k = floor((pi / 4) * sqrt(n_items))
    iterations = args.iterations if args.iterations and args.iterations > 0 else max(1, auto_k)

    # Sampler (Aer primitives)
    if args.backend != "local":
        print("Only 'local' backend is supported.", file=sys.stderr)
        sys.exit(EXIT_INVALID_INPUT)
    sampler = Sampler(options={"shots": args.shots, "seed_simulator": args.seed})

    # Grover algorithm
    np.random.seed(args.seed)
    grover = Grover(sampler=sampler, iterations=iterations)
    problem = AmplificationProblem(oracle)

    logging.info("=== Grover Local Search === target='%s' → %s, N=%d, n_bits=%d, k=%d, shots=%d, seed=%d",
                 args.target_key, target_binary, n_items, n_bits, iterations, args.shots, args.seed)

    try:
        result = grover.amplify(problem)
    except Exception as e:
        logging.exception("Grover amplify failed")
        print(f"Unexpected error: {str(e)}", file=sys.stderr)
        sys.exit(EXIT_INTERNAL_ERROR)

    # Extract top measurement and probabilities from SamplerResult (quasi distributions)
    elapsed_ms = round((time.time() - start_time) * 1000)
    raw_top = getattr(result, "top_measurement", None)
    top = (raw_top or "").zfill(n_bits) if n_bits > 0 else (raw_top or "")

    probabilities: Dict[str, float] = {}
    quasi = None
    try:
        sr = getattr(result, "sampler_result", None)
        if sr and hasattr(sr, "quasi_dists") and sr.quasi_dists:
            quasi = sr.quasi_dists[0]
    except Exception:
        quasi = None

    if quasi is not None:
        for k, v in dict(quasi).items():
            # Keys may be ints (bitstring-as-int) or strings; normalize to fixed-width bitstrings
            if isinstance(k, str):
                b = k.zfill(n_bits)
            else:
                b = format(int(k), f"0{n_bits}b")
            probabilities[b] = float(v)
        # Ensure we include 'top' key even if quasi has tiny zeros
        if top and top not in probabilities:
            probabilities[top] = 0.0
    else:
        # Fallback distribution if SamplerResult is missing
        if top:
            probabilities[top] = 1.0

    # Clamp negatives (rare numerical artifacts) and renormalize
    for b in list(probabilities.keys()):
        probabilities[b] = max(0.0, probabilities[b])
    s = sum(probabilities.values()) or 1.0
    probabilities = {b: (v / s) for b, v in probabilities.items()}

    # Synthetic depolarizing noise (optional, measurement-level)
    probabilities_noisy = None
    if args.noise and args.noise > 0.0:
        probabilities_noisy = depolarize_probs(probabilities, min(max(args.noise, 0.0), 1.0))

    confidence = float(probabilities.get(top, 0.0))

    # Top-K list
    top_k = None
    if args.topk and args.topk > 0:
        ordered = sorted(probabilities.items(), key=lambda kv: kv[1], reverse=True)
        top_k = [{"state": b, "p": float(p)} for b, p in ordered[: args.topk]]

    # Optional images
    circuit_b64 = render_circuit_png(n_bits, oracle, iterations) if args.render else None
    hist_b64 = render_histogram_png(probabilities) if args.render else None

    # Match back to entry
    matched_key = decoding.get(top)
    matched_value = entries.get(matched_key) if matched_key else None
    matched_index = keys.index(matched_key) if matched_key in keys else None

    note = "Success" if confidence >= 0.6 else "Low confidence"
    if not matched_key:
        note = "No match found"

    # Compose output JSON (stable shape for Java)
    out = {
        "quantum_result": {
            "matched_key": matched_key,
            "matched_value": matched_value,
            "matched_index": matched_index,
            "top_measurement": top,
            "oracle_expression": f"(x{target_binary})",
            "num_qubits": n_bits,
            "probabilities": probabilities,
            "probabilities_noisy": probabilities_noisy,
            "confidence_score": round(confidence, 6),
            "execution_time_ms": elapsed_ms,
            "oracle_depth": oracle.decompose().depth(),
            "iterations": iterations,
            "top_k": top_k,
            "circuit_png_b64": circuit_b64,
            "histogram_png_b64": hist_b64,
        },
        "scientific_notes": {
            "principle": "Grover's algorithm provides quadratic speedup for unstructured search.",
            "theory": (
                "Amplitude amplification finds a marked item in ~sqrt(N) iterations. "
                "Each iteration applies the oracle (phase flip) and the diffuser."
            ),
            "circuit_behavior": (
                "Initialize in superposition, apply the oracle to flip the target phase, "
                "then the diffuser to amplify its amplitude."
            ),
            "confidence_interpretation": (
                "Confidence reflects the probability mass on the reported bitstring under the Sampler model."
            ),
            "qubits": n_bits,
            "encoding_map": encoding,
            "used_iterations": iterations
        }
    }

    print(json.dumps(out))
    sys.exit(EXIT_SUCCESS)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        logging.exception("Unexpected error at top-level")
        print(json.dumps({"error": str(e)}))
        sys.exit(EXIT_INTERNAL_ERROR)

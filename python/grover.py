#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Grover key search (LOCAL/Aer) using Qiskit 1.x primitives (Sampler).
- Deterministic via --shots/--seed
- Robust endianness handling (auto-detects LE/BE from GroverResult)
- Returns rich JSON with matched entry, probabilities, top_k, and optional images (circuit/histogram)
"""

import sys
import json
import time
import base64
import argparse
import logging
import re
from io import BytesIO
from math import floor, pi, sqrt
from typing import Dict, List, Tuple, Optional

import numpy as np

from qiskit.circuit import QuantumCircuit
from qiskit_algorithms import Grover, AmplificationProblem
from qiskit_aer.primitives import Sampler
from qiskit.visualization import plot_histogram

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

EXIT_SUCCESS = 0
EXIT_COLLECTION_NOT_FOUND = 2
EXIT_INVALID_INPUT = 3
EXIT_INTERNAL_ERROR = 4

# ----------------------------- helpers ---------------------------------

def be_to_le(bits: str) -> str:
    return bits[::-1]

def le_to_be(bits: str) -> str:
    return bits[::-1]

_num_tail = re.compile(r".*?(\d+)$")

def sort_keys(keys: List[str], policy: str) -> List[str]:
    """ as-is | asc | desc (by numeric suffix, fallback to lexicographic) """
    if policy == "as-is":
        return list(keys)

    def key_fn(k: str):
        m = _num_tail.match(k)
        return (int(m.group(1)) if m else None, k)

    if policy == "asc":
        return sorted(keys, key=lambda k: (key_fn(k)[0] is None, key_fn(k)))
    elif policy == "desc":
        return sorted(keys, key=lambda k: (key_fn(k)[0] is None, key_fn(k)), reverse=True)
    return list(keys)

def encode_items(items: List[str]) -> Tuple[Dict[str, str], Dict[str, str], int]:
    """Contiguous BIG-ENDIAN codes in the given order."""
    n_bits = max(1, (len(items) - 1).bit_length())
    enc, dec = {}, {}
    for i, item in enumerate(items):
        b = format(i, f"0{n_bits}b")
        enc[item] = b
        dec[b] = item
    return enc, dec, n_bits

def build_bitstring_phase_oracle_le(target_le: str) -> QuantumCircuit:
    """Phase oracle: flip sign of |target_le| (LE)."""
    n = len(target_le)
    qc = QuantumCircuit(n, name=f"U|{target_le}>")
    for i, b in enumerate(target_le):
        if b == '0':
            qc.x(i)
    if n == 1:
        qc.z(0)
    else:
        qc.h(n - 1)
        qc.mcx(list(range(n - 1)), n - 1)
        qc.h(n - 1)
    for i, b in enumerate(target_le):
        if b == '0':
            qc.x(i)
    return qc

def diffuser(n: int) -> QuantumCircuit:
    qc = QuantumCircuit(n, name="diffuser")
    qc.h(range(n))
    qc.x(range(n))
    qc.h(n - 1)
    if n == 1:
        qc.z(0)
    else:
        qc.mcx(list(range(n - 1)), n - 1)
    qc.h(n - 1)
    qc.x(range(n))
    qc.h(range(n))
    return qc

def render_circuit_png(n_bits: int, oracle_circ: QuantumCircuit, iterations: int) -> Optional[str]:
    try:
        qc = QuantumCircuit(n_bits, name="grover")
        qc.h(range(n_bits))
        qc.compose(oracle_circ, range(n_bits), inplace=True)
        diff = diffuser(n_bits).to_instruction()
        for _ in range(max(0, iterations)):
            qc.append(diff, range(n_bits))
        buf = BytesIO()
        qc.draw(output="mpl")
        import matplotlib.pyplot as plt
        plt.tight_layout()
        plt.savefig(buf, format="png", bbox_inches="tight")
        plt.close()
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception as e:
        logging.warning("Circuit render failed: %s", e)
        return None

def render_histogram_png(prob_be: Dict[str, float]) -> Optional[str]:
    try:
        buf = BytesIO()
        fig = plot_histogram(prob_be, figsize=(6, 3))
        fig.savefig(buf, format="png", bbox_inches="tight")
        import matplotlib.pyplot as plt
        plt.close(fig)
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception as e:
        logging.warning("Histogram render failed: %s", e)
        return None

def depolarize_probs(prob_be: Dict[str, float], epsilon: float) -> Dict[str, float]:
    if epsilon <= 0:
        return prob_be
    keys = list(prob_be.keys())
    n_bits = len(keys[0]) if keys else 0
    uniform = 1.0 / (2 ** n_bits) if n_bits > 0 else 0.0
    noisy = {k: (1.0 - epsilon) * v + epsilon * uniform for k, v in prob_be.items()}
    s = sum(noisy.values()) or 1.0
    return {k: v / s for k, v in noisy.items()}

# --------- dynamic endianness detection for Sampler/GroverResult ----------

def detect_sampler_endianness(raw_top: Optional[str], n_bits: int, is_good_state_le) -> str:
    """
    Decide whether GroverResult.top_measurement (and quasi_dists keys) are LE or BE.
    We check the provided is_good_state (LE predicate) against raw and reversed.
    Returns 'LE' or 'BE'. Defaults to 'LE' if inconclusive.
    """
    s = (raw_top or "")
    if n_bits > 0:
        s = s.zfill(n_bits)
    try:
        if is_good_state_le(s):
            return "LE"
        if is_good_state_le(s[::-1]):
            return "BE"
    except Exception:
        pass
    return "LE"

# ------------------------------- main ------------------------------------

def main():
    start_time = time.time()

    parser = argparse.ArgumentParser(description="Grover key search via Qiskit 1.x Sampler.")
    parser.add_argument("target_key")
    parser.add_argument("keys_json")
    parser.add_argument("entries_json")
    parser.add_argument("--iterations", type=int, default=-1, help="-1 = auto (~pi/4*sqrt(N))")
    parser.add_argument("--backend", choices=["local"], default="local")
    parser.add_argument("--shots", type=int, default=1024)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--topk", type=int, default=0)
    parser.add_argument("--noise", type=float, default=0.0)
    parser.add_argument("--render", action="store_true")
    parser.add_argument("--key-order", choices=["as-is", "asc", "desc"], default="as-is",
                        help="Order keys for encoding_map by numeric suffix; stabilizes mapping.")
    args = parser.parse_args()

    # Parse input
    try:
        keys = json.loads(args.keys_json)
        entries = json.loads(args.entries_json)
        if not isinstance(keys, list) or not isinstance(entries, dict):
            print("Invalid JSON shape: expected list for keys and dict for entries", file=sys.stderr)
            sys.exit(EXIT_INVALID_INPUT)
    except Exception:
        print("Invalid JSON input", file=sys.stderr)
        sys.exit(EXIT_INVALID_INPUT)

    if args.target_key not in entries:
        print("CollectionNotFound", file=sys.stderr)
        sys.exit(EXIT_COLLECTION_NOT_FOUND)

    ordered_keys = sort_keys(keys, args.key_order)
    if args.target_key not in ordered_keys:
        print("CollectionNotFound", file=sys.stderr)
        sys.exit(EXIT_COLLECTION_NOT_FOUND)

    encoding_be, decoding_be, n_bits = encode_items(ordered_keys)
    target_be = encoding_be[args.target_key]
    target_le = be_to_le(target_be)

    # Oracle & Grover
    oracle = build_bitstring_phase_oracle_le(target_le)

    n_items = len(ordered_keys)
    auto_k = floor((pi / 4) * sqrt(n_items))
    iterations = args.iterations if (args.iterations and args.iterations > 0) else max(1, auto_k)

    if args.backend != "local":
        print("Only 'local' backend is supported.", file=sys.stderr)
        sys.exit(EXIT_INVALID_INPUT)

    sampler = Sampler()
    sampler.options.update_options(shots=args.shots)
    if args.seed is not None:
        sampler.options.update_options(seed_simulator=args.seed)

    np.random.seed(args.seed)

    def is_good_state_le(state) -> bool:
        """Predicate in LE for Grover internals."""
        if isinstance(state, str):
            s = state
        else:
            s = ''.join(str(int(x)) for x in state)
        return s == target_le

    problem = AmplificationProblem(oracle=oracle, is_good_state=is_good_state_le)

    logging.info("=== Grover Local Search === key=%s, target_be=%s, target_le=%s, N=%d, n_bits=%d, k=%d, shots=%d, seed=%d, order=%s",
                 args.target_key, target_be, target_le, n_items, n_bits, iterations, args.shots, args.seed, args.key_order)

    try:
        result = Grover(sampler=sampler, iterations=iterations).amplify(problem)
    except Exception as e:
        logging.exception("Grover amplify failed")
        print(f"Unexpected error: {str(e)}", file=sys.stderr)
        sys.exit(EXIT_INTERNAL_ERROR)

    elapsed_ms = round((time.time() - start_time) * 1000)

    raw_top = getattr(result, "top_measurement", None)
    endianness = detect_sampler_endianness(raw_top, n_bits, is_good_state_le)

    # Convert top & distribution according to detected endianness
    if raw_top is None:
        top_be = ""
        top_le = ""
    else:
        raw = raw_top.zfill(n_bits) if n_bits > 0 else raw_top
        if endianness == "LE":
            top_le = raw
            top_be = le_to_be(raw)
        else:
            top_be = raw
            top_le = be_to_le(raw)

    probabilities_be: Dict[str, float] = {}
    quasi = None
    try:
        sr = getattr(result, "sampler_result", None)
        if sr and hasattr(sr, "quasi_dists") and sr.quasi_dists:
            quasi = sr.quasi_dists[0]
    except Exception:
        quasi = None

    if quasi is not None:
        for k, v in dict(quasi).items():
            # normalize key string
            if isinstance(k, str):
                raw = k.zfill(n_bits)
            else:
                raw = format(int(k), f"0{n_bits}b")
            b_be = le_to_be(raw) if endianness == "LE" else raw
            probabilities_be[b_be] = float(v)
        if top_be and top_be not in probabilities_be:
            probabilities_be[top_be] = 0.0
    else:
        if top_be:
            probabilities_be[top_be] = 1.0

    # sanitize & renormalize
    for b in list(probabilities_be.keys()):
        probabilities_be[b] = max(0.0, probabilities_be[b])
    s = sum(probabilities_be.values()) or 1.0
    probabilities_be = {b: (v / s) for b, v in probabilities_be.items()}

    probabilities_noisy_be = None
    if args.noise and args.noise > 0.0:
        probabilities_noisy_be = depolarize_probs(probabilities_be, min(max(args.noise, 0.0), 1.0))

    confidence = float(probabilities_be.get(top_be, 0.0))

    top_k = None
    if args.topk and args.topk > 0:
        ordered = sorted(probabilities_be.items(), key=lambda kv: kv[1], reverse=True)
        top_k = [{"state": b, "p": float(p)} for b, p in ordered[: args.topk]]

    circuit_b64 = render_circuit_png(n_bits, oracle, iterations) if args.render else None
    hist_b64 = render_histogram_png(probabilities_be) if args.render else None

    matched_key = decoding_be.get(top_be)
    matched_value = entries.get(matched_key) if matched_key else None
    try:
        matched_index = keys.index(matched_key) if matched_key in keys else None
    except Exception:
        matched_index = None

    note = "Success" if confidence >= 0.6 else "Low confidence"
    if not matched_key:
        note = "No match found"

    # optional sanity flag: did Grover say top is good?
    oracle_eval = None
    try:
        # True iff top (in LE) satisfies is_good
        oracle_eval = bool(is_good_state_le(top_le))
    except Exception:
        oracle_eval = None

    out = {
        "quantum_result": {
            "matched_key": matched_key,
            "matched_value": matched_value,
            "matched_index": matched_index,
            "top_measurement": top_be,                      # BE for public API
            "oracle_expression": f"(x{target_be})",         # BE for consistency with encoding_map
            "num_qubits": n_bits,
            "probabilities": probabilities_be,              # BE keys
            "probabilities_noisy": probabilities_noisy_be,  # BE keys (optional)
            "confidence_score": round(confidence, 6),
            "execution_time_ms": elapsed_ms,
            "oracle_depth": oracle.decompose().depth(),
            "iterations": iterations,
            "top_k": top_k,
            "circuit_png_b64": circuit_b64,
            "histogram_png_b64": hist_b64,
            "note": note,
            "oracle_evaluation": oracle_eval,               # <— debug: is top actually "good" internally?
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
            "encoding_map": encoding_be,                    # BE
            "used_iterations": iterations
        },
        "debug": {
            "ordered_keys": ordered_keys,
            "target_key": args.target_key,
            "target_be": target_be,
            "target_le": target_le,
            "raw_top": raw_top,
            "top_be": top_be,
            "top_le": top_le,
            "sampler_endianness": endianness
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

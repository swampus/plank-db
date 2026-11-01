"""Offline, reproducible original/current compiler benchmark. Never a REST option.

Run: PYTHONPATH=python python -m oracle.benchmark --output results --repeats 3
All full-domain evaluation is explicit independent validation/benchmarking here.
"""
import argparse
import csv
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import platform
import random
import statistics
import sys
import tempfile
import time
from unittest.mock import patch

import numpy as np
import qiskit
import qiskit_aer
from qiskit import QuantumCircuit, transpile
from qiskit.quantum_info import Statevector
from qiskit_aer import AerSimulator
from . import cache
from .baseline import compile_oracle as original
from .compiler import compile_oracle as optimized, expected_qubits
from .circuits import grover_circuit
from .predicates import COMPILER_VERSION, canonical_json, normalize
from .tests.audit_reference import truth, leaf, join

BASIS = ["u", "cx"]
LEVEL = 1


def cases(width):
    maximum = (1 << width) - 1
    value = 9 & maximum
    eq = {"type": "EQUALS", "value": value}
    a, b, c = leaf(1, 1), leaf(2, 2), leaf(3, 0)
    result = {"equality": eq, "bitmask": leaf(3, 1),
              "and_overlap": join("AND", leaf(3, 1), leaf(6 & maximum, 4 & maximum)),
              "or_overlap": join("OR", leaf(3, 1), leaf(6 & maximum, 4 & maximum)),
              "nested_shared": join("AND", join("OR", a, b), join("OR", a, c)),
              "zero": join("AND", a, leaf(1, 0)),
              "all": join("OR", a, leaf(1, 0)), "redundant": join("AND", eq, eq)}
    if width == 4:
        result["reference"] = leaf(15, 9)
    if width == 8:
        result["and_disjoint_wide"] = join("AND", leaf(15, 9), leaf(240, 128))
    return result


def elapsed(start):
    return (time.perf_counter() - start) * 1000


def transpiled(circuit, seed):
    return transpile(circuit, basis_gates=BASIS, optimization_level=LEVEL, seed_transpiler=seed)


def costs(prefix, circuit):
    return {prefix + "_depth": circuit.depth(), prefix + "_cx": circuit.count_ops().get("cx", 0),
            prefix + "_two_qubit_gates": sum(len(i.qubits) == 2 for i in circuit.data)}


def verify_phase(width, predicate, nodes, build):
    oracle = build(width, predicate, nodes)
    preparation = QuantumCircuit(oracle.num_qubits)
    preparation.h(range(width))
    vector = Statevector.from_instruction(preparation.compose(oracle)).data
    expected = np.zeros(len(vector), complex)
    expected[:1 << width] = [(-1 if truth(predicate, x, width) else 1) / math.sqrt(1 << width) for x in range(1 << width)]
    np.testing.assert_allclose(vector, expected, atol=2e-9)


def pipeline(width, predicate, nodes, iterations, build, shots, seed):
    started = time.perf_counter()
    start = time.perf_counter()
    oracle = build(width, predicate, nodes)
    construction_ms = elapsed(start)
    start = time.perf_counter()
    circuit = grover_circuit(width, oracle, iterations)
    grover_ms = elapsed(start)
    start = time.perf_counter()
    # Same oracle/full ordering for both compilers and all repetitions.
    compiled = transpiled(circuit, seed)
    full_transpile_ms = elapsed(start)
    start = time.perf_counter()
    compiled_oracle = transpiled(oracle, seed)
    oracle_transpile_ms = elapsed(start)
    start = time.perf_counter()
    simulator = AerSimulator(method="statevector", device="CPU", max_parallel_threads=1, max_memory_mb=64)
    result = simulator.run(compiled, shots=shots, seed_simulator=seed).result()
    simulation_ms = elapsed(start)
    if not result.success:
        raise AssertionError("Aer execution failed")
    counts = result.get_counts()
    start = time.perf_counter()
    valid_count = sum(n for bits, n in counts.items() if truth(predicate, int(bits, 2), width))
    verification_ms = elapsed(start)
    start = time.perf_counter()
    solutions = sum(truth(predicate, x, width) for x in range(1 << width))
    classical_ms = elapsed(start)
    probability = math.sin((2 * iterations + 1) * math.asin(math.sqrt(solutions / (1 << width))))**2
    tolerance = 6 * math.sqrt(probability * (1 - probability) / shots) + 2 / shots
    if abs(valid_count / shots - probability) > tolerance:
        raise AssertionError("shot result outside six-sigma tolerance")
    return {"total_qubits": oracle.num_qubits, "ancilla_qubits": oracle.num_qubits - width,
            "statevector_bytes_estimate": 16 * (1 << oracle.num_qubits),
            **costs("oracle_logical", oracle), **costs("oracle_transpiled", compiled_oracle),
            **costs("full_logical", circuit), **costs("full_transpiled", compiled),
            "oracle_construction_ms": construction_ms, "grover_construction_ms": grover_ms,
            "oracle_transpilation_ms": oracle_transpile_ms, "full_transpilation_ms": full_transpile_ms,
            "transpilation_ms": oracle_transpile_ms + full_transpile_ms, "simulation_ms": simulation_ms,
            "verification_ms": verification_ms, "classical_brute_force_ms": classical_ms,
            "pipeline_total_ms": elapsed(started), "solution_count_validation": solutions,
            "ideal_success_probability_validation": probability, "observed_valid_probability": valid_count / shots,
            "valid_shots": valid_count, "top_bits": max(counts, key=counts.get),
            "top_valid": truth(predicate, int(max(counts, key=counts.get), 2), width)}


def summary(rows, group_fields):
    groups = {}
    for row in rows:
        key = tuple(row[field] for field in group_fields)
        groups.setdefault(key, []).append(row)
    output = []
    for key, samples in groups.items():
        numeric = [k for k, v in samples[0].items() if type(v) in (int, float) and k not in group_fields and k != "repeat"]
        stats = {}
        for field in numeric:
            values = [row[field] for row in samples]
            stats[field] = {"median": statistics.median(values), "min": min(values), "max": max(values),
                            "population_stddev": statistics.pstdev(values)}
        output.append({**dict(zip(group_fields, key)), "samples": len(samples), "stats": stats})
    return output


def environment(seed, shots, repeats):
    return {"timestamp_utc": datetime.now(timezone.utc).isoformat(), "python": sys.version,
            "platform": platform.platform(), "processor": platform.processor(), "cpu_count": os.cpu_count(),
            "qiskit": qiskit.__version__, "aer": qiskit_aer.__version__, "numpy": np.__version__,
            "compiler_version": COMPILER_VERSION, "basis_gates": BASIS, "optimization_level": LEVEL,
            "seed_transpiler": seed, "seed_simulator": seed, "shots": shots, "repeats": repeats,
            "simulator": {"method": "statevector", "device": "CPU", "max_parallel_threads": 1, "max_memory_mb": 64},
            "warmup": "one default pipeline per compiler discarded; core timings use a warm SDK and fresh circuits",
            "cache_cold_definition": "new empty application-cache directory in a warm interpreter; not OS cache cold",
            "pipeline_total_definition": "in-process construction through verification and explicit baseline; excludes interpreter/HTTP startup"}


def write_csv(path, rows):
    fields = list(dict.fromkeys(field for row in rows for field in row))
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def run_benchmarks(output, widths, repeats, shots, seed):
    output.mkdir(parents=True, exist_ok=True)
    builders = {"original": original, "optimized": optimized}
    rows, cache_rows = [], []
    reference, n = normalize(4, leaf(15, 9))
    for build in builders.values():
        pipeline(4, reference, n, 3, build, shots, seed)
    order = random.Random(17031)
    for width in widths:
        for name, raw in cases(width).items():
            predicate, nodes = normalize(width, raw)  # conservative ORIGINAL input budget, before any allocation
            for build in builders.values():
                verify_phase(width, predicate, nodes, build)
            iterations = (0, 1, 3, 6) if name == "reference" else (0, 1, 3)
            for count in iterations:
                for repeat in range(repeats):
                    names = list(builders)
                    order.shuffle(names)
                    for compiler in names:
                        row = {"compiler": compiler, "case": name, "width": width, "iterations": count,
                               "shots": shots, "seed": seed, "repeat": repeat, "predicate_json": canonical_json(predicate)}
                        row.update(pipeline(width, predicate, nodes, count, builders[compiler], shots, seed))
                        rows.append(row)
            for compiler, build in builders.items():
                with tempfile.TemporaryDirectory(prefix="plank-audit-cache-") as directory:
                    expected = (lambda w, p: w + normalize(w, p)[1]) if compiler == "original" else expected_qubits
                    # Private diagnostic override only. No compiler selection in production protocol.
                    with patch("oracle.cache.compile_oracle", build), patch("oracle.cache.expected_qubits", expected), \
                            patch("oracle.cache.COMPILER_VERSION", "audit-frozen-v1" if compiler == "original" else COMPILER_VERSION):
                        for repeat in range(repeats):
                            with patch.dict(os.environ, {"QUANTUM_ORACLE_CACHE_DIR": str(Path(directory) / str(repeat))}):
                                for temperature in ("cold", "warm"):
                                    start = time.perf_counter()
                                    result = cache.template(width, predicate, nodes)
                                    cache_rows.append({"compiler": compiler, "case": name, "width": width,
                                                       "temperature": temperature, "repeat": repeat,
                                                       "cache_wall_ms": elapsed(start), "compiler_ms": result[3],
                                                       "cache_inclusive_ms": result[4], "cache_hit": result[2]})
                                    assert result[2] == (temperature == "warm")
            print(f"completed width={width} case={name} pipeline_samples={len(rows)}", flush=True)
    report = {"environment": environment(seed, shots, repeats), "pipeline_samples": rows, "cache_samples": cache_rows,
              "pipeline_summary": summary(rows, ("compiler", "case", "width", "iterations")),
              "cache_summary": summary(cache_rows, ("compiler", "case", "width", "temperature"))}
    (output / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    write_csv(output / "pipelines.csv", rows)
    write_csv(output / "cache.csv", cache_rows)
    reference_rows = [row for row in rows if row["case"] == "reference" and row["iterations"] == 3]
    lab = {"environment": report["environment"], "request": {"width": 4, "predicate": reference, "iterations": 3, "shots": shots, "seed": seed},
           "measured_reference": summary(reference_rows, ("compiler", "case", "width", "iterations"))}
    (output / "lab-reference.json").write_text(json.dumps(lab, indent=2), encoding="utf-8")
    print(f"wrote {len(rows)} pipeline and {len(cache_rows)} cache samples to {output}", flush=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--widths", type=int, nargs="+", choices=(2, 3, 4, 5, 6, 8), default=[2, 3, 4, 5, 6, 8])
    parser.add_argument("--repeats", type=int, choices=range(1, 11), default=3)
    parser.add_argument("--shots", type=int, default=1024)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if not 1 <= args.shots <= 8192 or not 0 <= args.seed <= 2147483647:
        parser.error("shots/seed outside supported limits")
    run_benchmarks(args.output, args.widths, args.repeats, args.shots, args.seed)


if __name__ == "__main__":
    main()

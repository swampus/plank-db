"""Aer execution followed by a separately timed classical verification baseline."""
import math
import time
import qiskit
import qiskit_aer
from qiskit import transpile
from qiskit_aer import AerSimulator
from .cache import template
from .circuits import grover_circuit, select_iterations
from .predicates import (COMPILER_VERSION, SCHEMA_VERSION, analytical_count, bounded_env,
                         evaluate, integer, normalize)


def run(request, action="search"):
    started = time.perf_counter()
    if not isinstance(request, dict) or set(request) - {"width", "predicate", "iterations", "shots", "seed"}:
        raise ValueError("request must contain only width, predicate, iterations, shots, seed")
    if action not in ("explain", "search"):
        raise ValueError("unsupported action")
    normalization_start = time.perf_counter()
    width = request.get("width")
    predicate, nodes = normalize(width, request.get("predicate"))
    normalization_ms = (time.perf_counter() - normalization_start) * 1000
    shots = integer(request.get("shots", 1024), "shots", 1, bounded_env("QUANTUM_ORACLE_MAX_SHOTS", 8192, 8192))
    seed = integer(request.get("seed", 42), "seed", 0, 2147483647)
    count = analytical_count(predicate, width)
    iterations = select_iterations(width, count, request.get("iterations"), bounded_env("QUANTUM_ORACLE_MAX_ITERATIONS", 32, 32))
    oracle, fingerprint, hit, compilation_ms, cache_ms = template(width, predicate, nodes)
    construction_start = time.perf_counter()
    circuit = grover_circuit(width, oracle, iterations)
    construction_ms = (time.perf_counter() - construction_start) * 1000
    transpilation_start = time.perf_counter()
    # Fixed basis makes gate counts reproducible and exposes actual two-qubit cost.
    compiled = transpile(circuit, basis_gates=["u", "cx"], optimization_level=1, seed_transpiler=seed)
    compiled_oracle = transpile(oracle, basis_gates=["u", "cx"], optimization_level=1, seed_transpiler=seed)
    transpilation_ms = (time.perf_counter() - transpilation_start) * 1000
    simulation_ms = verification_ms = 0.0
    measurements = []
    top = None
    observed = None
    baseline = None
    if action == "search":
        simulation_start = time.perf_counter()
        simulator = AerSimulator(method="statevector", device="CPU", max_parallel_threads=1, max_memory_mb=64)
        result = simulator.run(compiled, shots=shots, seed_simulator=seed).result()
        if not result.success:
            raise RuntimeError("Aer simulation failed")
        counts = result.get_counts()
        simulation_ms = (time.perf_counter() - simulation_start) * 1000
        verification_start = time.perf_counter()
        for bits, n in sorted(counts.items()):
            # Only search qubits measured: strings are MSB->LSB, q[0] is LSB.
            value = int(bits, 2)
            valid = evaluate(predicate, value)
            measurements.append({"bits": bits, "value": value, "count": n, "probability": n / shots, "valid": valid})
        best = max(measurements, key=lambda m: (m["count"], -m["value"]))
        top = dict(best)
        observed = sum(m["probability"] for m in measurements if m["valid"])
        verification_ms = (time.perf_counter() - verification_start) * 1000
        # Explicit, separate brute-force benchmark AFTER quantum execution. Never used
        # to build oracle, choose iterations or fill theoretical probability.
        baseline_start = time.perf_counter()
        valid_states = [x for x in range(1 << width) if evaluate(predicate, x)]
        baseline = {"valid_states": valid_states, "solution_count": len(valid_states),
                    "elapsed_ms": (time.perf_counter() - baseline_start) * 1000}
    theoretical = None if count is None else math.sin((2*iterations+1)*math.asin(math.sqrt(count/(1 << width))))**2
    warnings = ["Local CPU Aer simulation; no hardware or QRAM. Virtual bit-vector domain, not database storage.",
                "Classical brute-force baseline is separate; simulator wall time is not a quantum speedup."]
    if count is None:
        warnings.append("Analytical cardinality unknown; manual iterations. Theoretical probability is unavailable.")
    if count == 0:
        warnings.append("Predicate has no solutions; Grover cannot create them.")
    if count == 1 << width:
        warnings.append("Every state satisfies the predicate; no amplification needed.")
    return {"compiler_version": COMPILER_VERSION, "schema_version": SCHEMA_VERSION,
            "normalized_predicate": predicate, "oracle_fingerprint": fingerprint,
            "backend": "local/aer_simulator/statevector/CPU", "qiskit_version": qiskit.__version__,
            "aer_version": qiskit_aer.__version__, "simulated": True, "cache_hit": hit,
            "analytical_solution_count": count, "iterations": iterations, "shots": shots, "seed": seed,
            "theoretical_success_probability": theoretical,
            "observed_valid_probability": observed, "measurements": measurements, "top_measurement": top,
            "classical_baseline": baseline,
            "metrics": {"search_qubits": width, "ancilla_qubits": oracle.num_qubits - width, "total_qubits": oracle.num_qubits,
                        "statevector_bytes": 16 * (1 << oracle.num_qubits),
                        "oracle_depth": compiled_oracle.depth(), "total_depth": compiled.depth(),
                        "gate_counts": dict(compiled.count_ops()),
                        "two_qubit_gates": sum(len(i.qubits) == 2 for i in compiled.data)},
            "timings": {"normalization_ms": normalization_ms, "compilation_ms": compilation_ms,
                        "cache_lookup_and_write_ms": cache_ms, "circuit_construction_ms": construction_ms,
                        "transpilation_ms": transpilation_ms, "simulation_ms": simulation_ms,
                        "verification_ms": verification_ms, "python_total_ms": (time.perf_counter()-started)*1000},
            "oracle_circuit": str(oracle.draw(output="text", fold=100)), "warnings": warnings}

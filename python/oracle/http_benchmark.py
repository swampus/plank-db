"""Measure isolated original/current REST deployments, keeping cold/hit labels real."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request
from .benchmark import summary, write_csv


def shape(value):
    if isinstance(value, dict):
        return {key: shape(item) for key, item in value.items()}
    if isinstance(value, list):
        return [shape(value[0])] if value else []
    if type(value) in (int, float):
        return "number"
    return type(value).__name__


def measure(base, compiler, samples):
    deadline = time.monotonic() + 30
    while True:
        try:
            with urllib.request.urlopen(base + "/v3/api-docs", timeout=2) as response:
                if response.status == 200:
                    break
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError("deployment did not become ready at " + base)
        time.sleep(.25)
    request = {"width": 4, "predicate": {"type": "BITMASK_EQUALS", "mask": 15, "value": 9},
               "iterations": 3, "shots": 1024, "seed": 42}
    rows, contract = [], None
    for repeat in range(samples):
        start = time.perf_counter()
        req = urllib.request.Request(base + "/api/rest/v1/oracle/search", data=json.dumps(request).encode(),
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=60) as response:
            report = json.load(response)
        if report["top_measurement"]["value"] != 9 or not report["top_measurement"]["valid"]:
            raise AssertionError("invalid search result")
        contract = shape(report)
        rows.append({"compiler": compiler, "repeat": repeat, "temperature": "warm" if report["cache_hit"] else "cold",
                     "client_http_ms": (time.perf_counter() - start) * 1000, "server_end_to_end_ms": report["end_to_end_ms"],
                     "oracle_construction_ms": report["timings"]["compilation_ms"],
                     "transpilation_ms": report["timings"]["transpilation_ms"],
                     "simulation_ms": report["timings"]["simulation_ms"],
                     "classical_brute_force_ms": report["classical_baseline"]["elapsed_ms"],
                     "cx": report["metrics"]["gate_counts"].get("cx", 0), "total_depth": report["metrics"]["total_depth"],
                     "oracle_depth": report["metrics"]["oracle_depth"], "total_qubits": report["metrics"]["total_qubits"],
                     "observed_valid_probability": report["observed_valid_probability"],
                     "compiler_version": report["compiler_version"], "qiskit_version": report["qiskit_version"],
                     "aer_version": report["aer_version"]})
    return rows, contract


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--optimized", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--samples", type=int, choices=range(2, 21), default=5)
    args = parser.parse_args()
    before, before_shape = measure(args.baseline, "original", args.samples)
    after, after_shape = measure(args.optimized, "optimized", args.samples)
    if before_shape != after_shape:
        raise AssertionError("REST field/type contract changed")
    rows = before + after
    args.output.mkdir(parents=True, exist_ok=True)
    report = {"request": {"width": 4, "mask": 15, "value": 9, "iterations": 3, "shots": 1024, "seed": 42},
              "notes": "Sequential measurements against isolated deployments; cache temperatures reflect actual responses. Cold cache is not cold JVM.",
              "response_field_contract_equal": True, "response_shape": before_shape,
              "samples": rows, "summary": summary(rows, ("compiler", "temperature"))}
    (args.output / "http.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    write_csv(args.output / "http.csv", rows)
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()

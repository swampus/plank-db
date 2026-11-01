"""Real HTTP regression/demo checks, run explicitly against a running local deployment.

python python/oracle/tests/smoke_http.py --output verification-results
Uses only the standard library. Exits nonzero on any failed check.
"""
import argparse
import concurrent.futures
import json
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8085")
    parser.add_argument("--output", default="verification-results")
    args = parser.parse_args()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    checks = []

    # Compose returns before Spring has started accepting HTTP connections.
    deadline = time.monotonic() + 30
    while True:
        try:
            with urllib.request.urlopen(args.base + "/v3/api-docs", timeout=2) as response:
                if response.status == 200:
                    break
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError("Spring did not become ready at " + args.base + " within 30 seconds")
        time.sleep(.25)

    def call(path, payload=None, method=None, expected=200):
        data = None if payload is None else json.dumps(payload).encode()
        request = urllib.request.Request(args.base + path, data=data, method=method,
                                         headers={"Content-Type": "application/json"} if data else {})
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        elapsed = (time.perf_counter() - start) * 1000
        checks.append({"path": path, "method": method or ("POST" if data else "GET"), "status": status, "elapsed_ms": elapsed})
        assert status == expected, (path, status, body.decode()[:2000])
        try:
            return json.loads(body)
        except ValueError:
            return body.decode()

    try:
        assert "Quantum Oracle Lab" in call("/oracle-lab/")
        assert "swagger" in call("/swagger-ui/index.html").lower()
        schema = call("/v3/api-docs")
        assert "/api/rest/v1/oracle/search" in schema["paths"]
        for operation in ("search", "explain"):
            request_schema = schema["paths"]["/api/rest/v1/oracle/" + operation]["post"]["requestBody"]
            assert request_schema["required"]
            assert "application/json" in request_schema["content"]
        name = "oracle-smoke-" + uuid.uuid4().hex[:8]
        collection = "/api/rest/v1/collections/" + name
        call(collection, method="POST")
        for i in range(1, 5):
            call(collection + "/entries", {"key": f"k{i}", "value": f"v{i}"})
        entries = call(collection + "/entries")
        assert "v2" in json.dumps(entries)
        legacy_search = call(collection + "/search", {"key": "k2"})
        (output / "legacy-search.json").write_text(json.dumps(legacy_search, indent=2))
        legacy_range = call(collection + "/range", {"fromKey": "k2", "toKey": "k4"})
        (output / "legacy-range.json").write_text(json.dumps(legacy_range, indent=2))
        explain = call(collection + "/explain", {"mode": "KEY", "key": "k2", "backend": "local", "shots": 128, "seed": 42})
        assert explain["plan"]["marked_states"]
        call(collection + "/entries", {"key": "k4"}, method="DELETE")
        call(collection, method="DELETE")
        payload = {"width": 4, "predicate": {"type": "BITMASK_EQUALS", "mask": 15, "value": 9}, "shots": 1024, "seed": 42}
        for iterations in (0, 3, 6):
            request = dict(payload, iterations=iterations)
            result = call("/api/rest/v1/oracle/search", request)
            (output / f"aer-iterations-{iterations}.json").write_text(json.dumps(result, indent=2))
            assert result["simulated"]
            assert result["backend"] == "local/aer_simulator/statevector/CPU"
            assert result["classical_baseline"]["valid_states"] == [9]
            assert sum(m["count"] for m in result["measurements"]) == 1024
            if iterations == 3:
                assert result["top_measurement"]["value"] == 9 and result["top_measurement"]["valid"]
                assert result["observed_valid_probability"] > .93
                assert abs(result["observed_valid_probability"] - result["theoretical_success_probability"]) < .04
            else:
                assert result["observed_valid_probability"] < .12
        report = call("/api/rest/v1/oracle/explain", dict(payload, iterations=3))
        assert report["cache_hit"]
        assert report["top_measurement"] is None and report["observed_valid_probability"] is None
        for predicate, count in [({"type": "BITMASK_EQUALS", "mask": 3, "value": 1}, 4),
                                 ({"type": "BITMASK_EQUALS", "mask": 0, "value": 0}, 16),
                                 ({"type": "AND", "children": [{"type": "EQUALS", "value": 1}, {"type": "EQUALS", "value": 2}]}, 0)]:
            result = call("/api/rest/v1/oracle/search", dict(payload, predicate=predicate))
            assert result["analytical_solution_count"] == count
            assert result["classical_baseline"]["solution_count"] == count
        or_predicate = {"type": "OR", "children": [{"type": "EQUALS", "value": 1}, {"type": "EQUALS", "value": 2}]}
        call("/api/rest/v1/oracle/search", dict(payload, predicate=or_predicate), expected=400)
        result = call("/api/rest/v1/oracle/search", dict(payload, predicate=or_predicate, iterations=2))
        assert result["theoretical_success_probability"] is None
        assert result["observed_valid_probability"] > .90
        for update in ({"width": 9}, {"shots": 1000000}, {"iterations": 33}, {"seed": -1}):
            call("/api/rest/v1/oracle/search", dict(payload, **update), expected=400)
        def concurrent_call(_):
            return call("/api/rest/v1/oracle/search", dict(payload, iterations=3))
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(concurrent_call, range(2)))
        assert all(r["cache_hit"] and r["top_measurement"]["valid"] for r in results)
        print(f"PASS: {len(checks)} real HTTP checks against {args.base}")
    finally:
        (output / "http-checks.json").write_text(json.dumps(checks, indent=2))


if __name__ == "__main__":
    main()

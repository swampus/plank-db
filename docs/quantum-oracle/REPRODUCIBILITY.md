# Reproduce the local Quantum Oracle Lab

## Docker (recommended)

Run from the repository root with Docker Compose and no IBM credentials:

```bash
cp .env.example .env
mvn clean test
mvn clean package
docker compose build
docker compose up -d
docker compose ps
docker compose exec -T plank-db /opt/venv/bin/python -m unittest discover -s python/oracle/tests -v
python python/oracle/tests/smoke_http.py --output verification-results
```

On Windows, `Copy-Item .env.example .env` replaces `cp`. If Maven is not on PATH, use its installed `mvn.cmd` path. Java source/target is 17; Docker builds and runs with Java 17. The host test run can also use a newer JDK. Inspect `docker compose logs` for the `Oracle search backend=local/aer_simulator/statevector/CPU` entries, Qiskit/Aer versions, cache status and measured simulation time. Never provide IBM credentials for this demo.

Open **http://localhost:8085/oracle-lab/**. Swagger remains **http://localhost:8085/swagger-ui.html**, and OpenAPI JSON remains **http://localhost:8085/v3/api-docs**.

The repository contains two historical override filenames. Docker reports which it chose. To avoid ambiguous automatic overrides use `docker compose -f docker-compose.yml build` and the same `-f` option for up/ps/exec/logs. The base configuration is sufficient for the local demo. The example environment retains port 8085; it must match the published `8085:8085` mapping. The default without `QUANTUM_ORACLE_ENABLED=true` returns 503 from oracle operations while collection routes continue to run.

`docker compose down` stops this demo. The named oracle cache survives. Do not delete its volume if you want persistent hits. Cache identity includes the compiler/schema/Qiskit version, so incompatible versions automatically miss and old files are eventually evicted.

## Sample requests

```bash
curl -sS http://localhost:8085/api/rest/v1/oracle/explain \
  -H 'Content-Type: application/json' \
  -d '{"width":4,"predicate":{"type":"BITMASK_EQUALS","mask":15,"value":9},"iterations":3,"shots":1024,"seed":42}'

curl -sS http://localhost:8085/api/rest/v1/oracle/search \
  -H 'Content-Type: application/json' \
  -d '{"width":4,"predicate":{"type":"BITMASK_EQUALS","mask":15,"value":9},"iterations":3,"shots":1024,"seed":42}'
```

Repeat search to see `cache_hit: true` and `compilation_ms: 0`. The oracle fingerprint is unchanged by iterations/shots/seed. Try zero, three and six iterations to observe overshooting. Omit `iterations` for analytically counted EQUALS, BITMASK_EQUALS and AND predicates. Supply it for OR. Use mask 3/value 1 for four solutions, mask 0/value 0 for all states, or AND(EQUALS(1),EQUALS(2)) for none. Full AST examples are in [architecture](ARCHITECTURE.md).

`smoke_http.py` tests the actual running service, exercises legacy collection CRUD/search/range/explain and Swagger, validates new search/explain/error contracts, makes concurrent requests and saves raw Aer iteration results to `verification-results/`. It exits nonzero on any mismatch. Requests use a uniquely named temporary collection, which is removed after the legacy checks. The script never asks for IBM mode. Keep generated raw evidence local (directory is gitignored).

## Python without Docker

```bash
python -m venv .venv
# Linux/macOS
.venv/bin/python -m pip install -r requirements.txt
PYTHONPATH=python .venv/bin/python -m unittest discover -s python/oracle/tests -v
# Windows PowerShell
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:PYTHONPATH='python'
.\.venv\Scripts\python.exe -m unittest discover -s python/oracle/tests -v
```

For host Spring execution, set `QUANTUM_PYTHON_EXEC` to the virtualenv interpreter; `QUANTUM_ORACLE_PYTHON_PATH` to the absolute repository `python` directory; and `QUANTUM_ORACLE_CACHE_DIR` to a writable local directory. Also configure the existing legacy `QUANTUM_LOCAL_SCRIPT_PATH`, `QUANTUM_LOCAL_RANGE_SCRIPT_PATH` and `QUANTUM_LOCAL_EXPLAIN_SCRIPT_PATH` when testing legacy scripts outside `/app`. Enable `QUANTUM_ORACLE_ENABLED=true`. Then run the packaged `web/target/plank-db.jar`.

Host `requirements.txt` covers the Oracle extension's Qiskit/Aer/algorithms dependencies. Legacy RANGE additionally needs tweedledum, and the legacy hardware adapters have their own IBM runtime dependencies. Docker includes these existing legacy packages. A private `.env` is read by Compose; a host-launched Java process needs exported environment variables. `environment.yml` is a historical Qiskit 0.45 environment and does not reproduce the pinned 1.1.1/Aer 0.14.2 release setup.

The new Python implementation adds no package dependencies beyond existing Qiskit/Aer and NumPy. Qiskit 1.1.1, Aer 0.14.2 and algorithms 0.2.2 remain pinned. Docker additionally retains the original legacy runtime, tweedledum and dotenv packages. Exact seeded counts are environment/version dependent; mathematical statevector assertions and tolerance-based amplification checks are the reproducibility criterion.

To repeat the additional Java 17 regression check in the Maven image (without needing Java 17 installed on the host):

```bash
docker build --target builder -t plankdb-oracle-test-builder .
docker run --rm plankdb-oracle-test-builder mvn clean test
```

## Tests and timing interpretation

Maven tests cover legacy regression, new resource validation, strict JSON contracts, disabled feature, error status mapping, malformed/nonzero subprocess results, both output limits, concurrent saturation, timeout cleanup and slot release. Process tests launch a portable Java test fixture and do not require Python installed on the host. Python tests independently cover oracle signs/ancillas, bit ordering, symbolic cardinality, Grover dynamics, Aer shots/seeds, QPY canonical hits/invalidation/corruption/bounds and multiprocess reuse.

Timing fields separate normalization, compilation, cache load/write, circuit construction, transpilation, Aer execution, and measured-result verification. `classical_baseline.elapsed_ms` is a separate full-domain enumeration. Python total includes those Python stages, while `end_to_end_ms` additionally includes Python import/startup, process bridge and Java mapping. The browser separately reports client HTTP time. These stages are measured with monotonic clocks, and overlapping/inclusive fields must not be summed as independent costs. On hits, compiler work is zero; cache lookup/lock/load is not zero.

The maximum statevector amplitude allocation is 1 MiB at the 16-qubit ceiling, with additional process/SDK/transpilation overhead. Bounds are deliberately conservative for a local demonstration. Lower the configured maxima for smaller machines; hard ceilings cannot be raised without revisiting and testing the implementation.

## Independent audit and repeated compiler benchmarks

The original compiler is frozen in `python/oracle/baseline.py`. It is used only by tests and the offline benchmark, with no compiler selector added to REST or the production stdin protocol. The current compiler is v2; response field names/types are unchanged. The 15 original Python test methods remain, supplemented by generated independent mathematical checks, cost-model checks and cache failure-injection tests. One fixture copies its initial vector to avoid Qiskit input aliasing; its exact assertions are unchanged.

Run the full matrix in the pinned CPU Aer image from the repository root:

```bash
docker compose build
docker run --rm --entrypoint /opt/venv/bin/python \
  -e PYTHONPATH=/audit/python -v "$PWD:/audit" -w /audit \
  plank-db-plank-db:latest -m oracle.benchmark \
  --output docs/quantum-oracle/benchmarks/repeated-run --repeats 3
```

In Windows PowerShell use `-v "${PWD}:/audit"` for the volume argument. The recorded audit command used `-v C:/srdev/work/plank-db:/audit`. The harness validates the original input budget before allocating any oracle and independently checks phases for each case. It measures widths 2, 3, 4, 5, 6, 8; eight predicate families, plus the reference and wide disjoint-mask regression; iterations 0, 1, 3, plus reference overshooting at 6; both compilers; and three repetitions. This produces 906 real Aer pipeline samples and 600 cache samples. `--widths 4 --repeats 3` is a smaller subset. No new package dependencies are needed.

Outputs are `results.json` (environment, every sample and summary), `pipelines.csv`, `cache.csv`, and `lab-reference.json`. Statistics include median, minimum, maximum and population standard deviation. One warmup pipeline per compiler is discarded; compiler order is shuffled deterministically within each timing repetition. Oracle-only and full-Grover construction/depth/transpilation/CX/two-qubit costs are separate. The fixed seed makes repeated shot counts deterministic, not independent statistical replicates. Timing repeats measure timing variability; quantum shot success is checked against an independent count using a six-sigma tolerance.

Cold cache means a fresh application cache directory inside a warm interpreter, not cold SDK/OS caches. Both compilers use the same hardened cache harness for this comparison; original register expectations are supplied privately by the benchmark. Every cold lookup must miss and every following warm lookup must hit. File locking, limits and integrity checks stay enabled. Offline pipeline totals exclude imports, HTTP and Java. Classical enumeration is separately timed after Aer execution, using the independent audit evaluator.

For end-to-end measurements, the audit preserved its already-running pre-audit image as `plankdb-oracle-audit-baseline` before any changes. Against isolated original/current deployments on IPv4 ports 18085/18086:

```bash
PYTHONPATH=python .venv/bin/python -m oracle.http_benchmark \
  --baseline http://127.0.0.1:18085 --optimized http://127.0.0.1:18086 \
  --output docs/quantum-oracle/benchmarks/repeated-http --samples 5
```

The HTTP harness waits for OpenAPI readiness and verifies the default response field/type contract matches. To reproduce cache-cold HTTP samples, start each deployment with a new private cache and do not run search smoke tests first. No cache deletion on the main demo is necessary. Use the same address family and container bindings: an initial `localhost` run showed an approximately two-second connection delay on one binding. Those rejected transport-comparison samples are retained as `http-initial-localhost.json/csv`. The corrected IPv4 records are `http.json/csv`.

The pre-audit full server image is a local retained artifact, not committed or published; exact original-image HTTP replay requires that image or the pre-audit source snapshot. A fresh checkout can reproduce all original/current oracle, full-Grover and hardened-cache benchmarks from the frozen compiler. This distinction avoids claiming that an unavailable original deployment can be reconstructed from a public REST selector.

The lab serves a compact measured reference file at `/oracle-lab/audit-reference.json`. It displays before/after data only when request parameters and SDK/compiler versions match that recorded reference; other requests retain live measured results without extrapolating old benchmark figures. Replace that static file with a newly generated `lab-reference.json` and rebuild if intentionally updating the reference. The comparison adds no REST fields or API options.

The optional browser smoke test is `python/oracle/tests/browser_smoke.cjs`. It uses a separately installed Playwright module, without adding a frontend build or runtime dependency. With `playwright` resolvable by Node and a Playwright Chromium installed:

```bash
node python/oracle/tests/browser_smoke.cjs
```

Alternatively set `PLAYWRIGHT_MODULE` to an existing Playwright module path and `PLAYWRIGHT_BROWSER` to a Chromium executable. `ORACLE_LAB_BASE` defaults to `http://localhost:8085`; screenshots/results default to gitignored `verification-results/audit-browser`. Checks run live simulation, the recorded comparison, nonmatching-parameter behavior, overshooting, AND/OR, mobile layout and both lab URL spellings. Run browser and the two-request HTTP concurrency test sequentially, because the service intentionally admits only two simulator subprocesses at once.

## Release-candidate review

`python scripts/check_release.py --output verification-results/release/review.json` audits the Git index using only the standard library. Run after staging the intended candidate. It checks artifact exclusions and credential patterns, verifies legacy source preservation except the intentional OpenAPI version metadata, checks 0.2.0 module versions, recomputes recorded benchmark summaries, compares CSV/JSON samples and validates the lab reference. This is a current-tree/index check, not a complete Git-history scan or an external security certification. See [release validation](../releases/RELEASE_VALIDATION.md) for the commands actually executed for this candidate.

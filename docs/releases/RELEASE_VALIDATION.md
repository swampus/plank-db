# PlankDB 0.2.0 release-candidate validation

Prepared on 2026-10-31. This records commands actually executed during release preparation, separately from the historical implementation/audit measurements. The candidate is suitable for an **experimental source release with the documented limitations**. It has not been tagged, pushed, merged, published as a GitHub release, or submitted to the QOSF listing.

## Candidate and compatibility review

- Root/module Maven versions and internal module dependencies are 0.2.0. The packaged JAR contains `domain-0.2.0.jar`, `application-0.2.0.jar` and `infrastructure-0.2.0.jar`.
- The deployed `/v3/api-docs` reports version 0.2.0 and includes both collection and oracle routes.
- The proposed index was compared with pre-feature commit `1b96554a4f829e5b804e03d255fec12af0e7ed90`: all 101 existing Java/Python source files are preserved except the intentional OpenAPI `.version("1.0.0")` → `.version("0.2.0")` metadata change. Existing public interfaces, constructors, DTO fields and legacy algorithm implementations were not refactored.
- All implementation/audit changes had session ownership established. No unrelated user modification was staged. The pre-existing `plankDB.zip` remains local and ignored.
- Existing Maven duplicate-declaration warnings and Compose override/version warnings remain visible; they did not prevent the executed builds/tests. They were not hidden through unrelated refactoring.

## Executed checks

Host: Windows 11, Maven 3.9.9, Eclipse Adoptium Java 21.0.12 compiling with `release=17`, Python 3.12. Docker runtime/build stages use Java 17; the local simulator is Qiskit 1.1.1/Aer 0.14.2, with Docker NumPy 1.26.4. Host Maven was invoked using its installed `mvn.cmd` path because it was not on PATH.

| Command/check | Actual result |
| --- | --- |
| `mvn clean test` | 53 tests passed; zero failures, errors or skipped tests; BUILD SUCCESS, 33.309 s |
| `mvn clean package` | Same 53 tests passed; BUILD SUCCESS, 39.029 s; executable JAR built with 0.2.0 internal modules |
| `PYTHONPATH=python .venv/.../python -m unittest discover -s python/oracle/tests -v` | All 26 methods passed, including all original 15; 12.069 s |
| `docker compose config --quiet` | Valid configuration; pre-existing override/version warnings remain |
| `docker compose build` | Succeeded; Java 17/Aer runtime image built from candidate sources |
| `docker compose up -d` and `docker compose ps` | Running; port `8085:8085` published |
| `docker compose exec -T plank-db /opt/venv/bin/python -m unittest discover -s python/oracle/tests -v` | All 26 methods passed; 17.051 s |
| `docker build --target builder -t plankdb-oracle-test-builder .` followed by `docker run --rm plankdb-oracle-test-builder mvn clean test` | Candidate 0.2.0 built and all 53 tests passed on Java 17; BUILD SUCCESS, 16.825 s |
| `python python/oracle/tests/smoke_http.py --output verification-results/release/http` | 29 checks passed, including legacy CRUD/search/range/explain, Swagger, Oracle explain/search, validation and concurrency |
| `node python/oracle/tests/browser_smoke.cjs` with an existing Playwright/Chromium installation | PASS: live Aer, bit grid, recorded comparison, unmatched-settings behavior, overshooting, AND/OR, desktop/mobile layout, both lab URL forms, no JavaScript errors |
| OpenAPI version/routes check | Deployed version is 0.2.0; legacy and oracle routes both present |
| Full documented benchmark replay | 906 real Aer pipeline samples and 600 cache samples completed; all original/current circuit metrics match retained evidence |
| `python scripts/check_release.py` against the proposed index | Artifact exclusions, credential-pattern scan, legacy preservation, version alignment and recorded-evidence consistency passed; rerun on the final staged snapshot before commit |

Python commands use `.venv/Scripts/python.exe` and `$env:PYTHONPATH='python'` on this Windows host. The browser test used `PLAYWRIGHT_MODULE`, `PLAYWRIGHT_BROWSER` and `ORACLE_BROWSER_OUTPUT=verification-results/release/browser`; no frontend dependency was added to the application.

The fresh benchmark replay used the documented container harness:

```bash
docker run --rm --entrypoint /opt/venv/bin/python \
  -e PYTHONPATH=/audit/python -v C:/srdev/work/plank-db:/audit -w /audit \
  plank-db-plank-db:latest -m oracle.benchmark \
  --output docs/quantum-oracle/benchmarks/repeated-release-check --repeats 3
```

The replay directory is intentionally ignored and was not substituted for the retained audit results. The original 906/600 samples, negative findings, HTTP transport-confounded samples and corrected IPv4 samples remain intact. Recorded JSON summaries were recomputed from samples, CSV and JSON rows compared, and the static lab reference matched to the evidence file.

## Repository hygiene and evidence scope

`.env`, virtual environments, logs, temporary files, QPY cache entries, screenshots/test output, benchmark `repeated-*` scratch directories and `plankDB.zip` are excluded. The previously tracked `log.txt` is removed from the index and retained locally. The original project PNG logos are unchanged source assets; no generated JAR/class/QPY/archive or new binary is included in the candidate.

`scripts/check_release.py` examines the actual Git index, reports paths/rules rather than suspected secret values, checks local documentation links, and verifies recorded scientific evidence. Its credential-pattern scan found no embedded credentials in the reviewed candidate. This is not a complete history scan, a guarantee covering every secret format, or an external security certification. Local private configuration was not printed or overwritten; its demo execution mode was verified LOCAL before invoking searches.

## Documentation consistency and release limits

- README port examples consistently use 8085; `.env.example` copy commands have destinations, JAR/requirements/configuration paths are corrected, and Java 21 host-test evidence replaces the old incompatibility claim.
- The README, changelog and draft release notes distinguish oracle-only CX 72 → 14 from full three-iteration CX 258 → 84 and depth 455 → 158. All refer to the documented pinned compilation conditions. Historical timings remain labeled historical; no robust end-to-end or database speedup is asserted.
- The oracle operates on a virtual bounded integer domain, without arbitrary record loading or QRAM. General OR requires manual iterations. The known legacy RANGE defect and legacy KEY synthetic value behavior remain documented and unchanged.
- Tests use an independent classical evaluator; no external certification or real-hardware verification is claimed. IBM execution behavior is preserved and real hardware calls were not performed.
- Exact original full HTTP replay still requires the retained local pre-audit image or original snapshot. Frozen compiler/core/cache comparisons can be reproduced from source. This limitation remains explicit.

The commit containing this document identifies the reviewed release candidate. Use `git log -1 --format=fuller` and `git status --short` to inspect its hash and working-tree state. Publishing, tagging, merging, creating a GitHub release/PR and updating QOSF remain separate maintainer actions.

# Changelog

## [0.2.0] — Unreleased release candidate

### Added

- Optional experimental Quantum Oracle Index with typed EQUALS, BITMASK_EQUALS, AND and OR predicates over a bounded virtual bit-vector domain.
- Logic-compiled reversible phase oracles, local Qiskit Aer/Grover execution, independent classical verification and explicit circuit/timing metrics.
- Additive `/api/rest/v1/oracle/explain` and `/api/rest/v1/oracle/search` endpoints and a self-contained Oracle Lab at `/oracle-lab/`.
- Persistent bounded QPY template caching with versioned fingerprints, checksummed payloads, process-safe locking, atomic writes and resource validation.
- Adversarial statevector tests, cache failure-injection tests, browser smoke checks, and reproducible original/optimized CPU simulator benchmarks in CSV/JSON.

### Changed

- Safe compiler optimizations include direct phase controls, bounded Boolean simplification, shared subexpressions and removal of unnecessary truth ancillas.
- Under the documented four-bit reference settings, oracle CX gates changed 72 → 14, full three-iteration Grover CX gates 258 → 84, and full depth 455 → 158. No robust end-to-end speedup or database speedup has been demonstrated.
- Maven module versions and OpenAPI metadata are aligned to 0.2.0; Java 17 and existing module/API structure are preserved.
- Docker retains its Maven build and Qiskit pins, uses an available Temurin Java 17 runtime base, persists the oracle cache and consistently exposes port 8085. A credential-free `.env.example` enables the optional demo.
- Local archives, runtime logs, caches, virtual environments, credentials and benchmark scratch outputs are excluded from version control. Recorded reproducible benchmark evidence is retained.

### Compatibility and known limitations

- Existing Java public interfaces/constructors, collection endpoints, DTO fields, legacy Grover scripts and LOCAL/IBM execution behavior are preserved. Oracle execution is local-only; no IBM hardware validation was performed for this candidate.
- The virtual domain is `0 <= x < 2^width`, with 2–8 search qubits and a conservative 16-total-qubit budget. This is not QRAM or an index over arbitrary stored records.
- General OR predicates require explicitly supplied Grover iterations; no approximate or enumerated count is used in normal execution.
- The known legacy RANGE defect remains: populated multi-key ranges may return HTTP 200 with “No match found” and inconsistent decoded bit widths. It is documented and untouched. Legacy KEY synthetic `matched_value` behavior is also unchanged.
- Simulator measurements do not establish hardware performance, scientific novelty or a database advantage. The pre-audit full HTTP baseline image is a local artifact; frozen compiler/core/cache benchmarks are reproducible from committed source.

See [draft release notes](docs/releases/v0.2.0.md), [audit](docs/quantum-oracle/INDEPENDENT_AUDIT.md), [optimization results](docs/quantum-oracle/OPTIMIZATION_RESULTS.md) and [reproduction commands](docs/quantum-oracle/REPRODUCIBILITY.md).

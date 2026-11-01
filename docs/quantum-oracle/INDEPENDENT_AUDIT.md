# Independent audit: CORRECT WITH LIMITATIONS

This is the historical pre-release audit record. Subsequent 0.2.0 version metadata, repository cleanup and release-check reruns are documented in [release validation](../releases/RELEASE_VALIDATION.md); they do not replace the recorded audit measurements or expand its verification claims.

Audit date: 2026-10-31. This report treats the original results as claims to reproduce. The work began on `feature/quantum-oracle-index` with an already-dirty working tree; all prior implementation work and the unrelated `plankDB.zip` were preserved. No commits, pushes, releases, PRs, legacy algorithm fixes, Java API changes or Docker configuration changes were made by this audit.

## Verdict and reproduced baseline

**CORRECT WITH LIMITATIONS.** No counterexample was found in the original Boolean compiler's clean-work phase semantics, bit ordering, diffusion or Grover formula. The optimized compiler passes the same checks and a larger independent adversarial corpus. This is evidence for the supported bounded domain, not a proof of optimal synthesis, hardware performance or correctness for arbitrary future operators.

For width 4, mask 15, value 9, three iterations, 1024 shots and seed 42, the original result reproduced: 992 valid shots (0.96875), ideal success 0.9613189697265625. Its oracle has logical depth 6 and, after `u,cx`, optimization level 1, seed 42, depth 129 and 72 CX. The **258 CX** figure is for the **complete three-iteration Grover circuit**, including three oracle calls and three diffusion operations after full-circuit transpilation, with depth 455. It is not the oracle-only count. The baseline full logical depth is 36.

The original compiler is retained byte-for-byte in `python/oracle/baseline.py`. The pre-audit full Docker image was also tagged locally for HTTP comparison. Production has no baseline selector in REST or its stdin protocol.

## Confirmed issues and fixes

| Finding | Reproduction / root cause | Fix and verification |
| --- | --- | --- |
| Parseable altered QPY accepted as a cache hit | Append Z to a cached circuit, keep identity metadata and register size, serialize valid QPY. The old cache returned a hit; the expected -1 amplitude for basis state 1 became +1. Identity checks did not verify the serialized gates. | Cache format 2 verifies a SHA-256 payload digest before QPY parsing. Tests inject parseable changed gates with a stale digest and require a miss/rebuild and equivalent rebuilt phases. |
| Cache helper could bypass resource validation | Directly call `template(4, EQUALS(1), 1)` with a three-qubit limit: old helper constructed five qubits. Normal REST requests were protected by earlier validation, so this was not a demonstrated REST bypass. | Cache independently validates width/tree budget and node count before lookup/allocation. Cold and existing-entry cases are tested. |
| Expected test vector aliased by Qiskit evolution | The original fixture passed its expected NumPy array directly to Statevector. A constant-true oracle represented by global phase pi exposed Qiskit's mutation of shared input data, causing false phase failures. | Copy the input array before evolution. All exact signed-amplitude and ancilla assertions remain unchanged; all original 15 test methods pass. |
| Candidate optimization increased hardware-relevant cost | Unconditionally merging two disjoint four-bit constraints produced one eight-bit MCZ: 380 CX versus 156 in the original oracle. It was mathematically correct but costly. | Reject this transformation using a phase/compute-aware bound, verified against actual pinned MCX decompositions. The final oracle uses 145 CX; the bad candidate is not the production choice. |
| Initial HTTP transport comparison was confounded | Original-container `localhost` client times included an approximately two-second connection delay absent on the other port, unrelated to the server's measured time. | Retain the initial raw records, reject client speedup inference, and measure fresh isolated deployments with matching IPv4 bindings and URLs. |

The checksum detects corruption; it does not authenticate against a malicious authorized cache writer who can update the checksum. Cache directories remain trusted application storage. There is no upload or client-controlled cache path. No unsafe pickle, shell execution or new dependencies were introduced.

## Independent mathematical verification

`audit_reference.py` defines a separate evaluator using per-bit comparisons and arithmetic Boolean composition. It imports neither production `evaluate`, cardinality algebra nor compiler routines. Its deterministic corpus includes redundant identical subexpressions, overlapping masks, contradictions, tautologies, subset unions, nested AND/OR and generated trees.

`test_independent_audit.py` compares both original and current compilers against that evaluator. It exhaustively checks all basis states for 114 predicates across widths 2, 3 and 4; tests larger-width superpositions through width 8; and additionally tests transpiled signed amplitudes. Tests compare the whole expected vector, including zero amplitude on dirty work registers, and check a common global phase over superpositions. This avoids the vacuous per-basis-state global-phase test. Generated Grover checks include iterations 0, 1, 2 and 4 and verify the ideal success formula with clean ancillas. Cardinality checks and all 386 possible M values across widths 2, 3, 4, 5, 6, 8 check first-peak selection and zero/all degeneracies.

The benchmark independently verifies uniform-superposition phase equivalence for every benchmark case before timing it. Shot runs are real CPU Aer executions with finite-sample tolerances. Fixed seeds make repeated counts reproducible within an implementation; equivalent circuits with different register sizes can consume simulator randomness differently. The optimized reference produced 997/1024 valid shots versus 992/1024 originally, while exact ideal success was unchanged. This is not an improvement in the algorithm's theoretical success probability.

## Implemented transformations and their bounds

The compiler directly phase-marks equality/mask cubes, folds constants and exact local identities, conditionally merges intersections, simplifies cube subsumption/adjacency, removes the root truth ancilla, and shares computed subexpressions in a reversible DAG. Generic compound roots phase-mark child truth bits directly and uncompute the complete child DAG. Cheap root ORs use the exact XOR phase identity; unrestricted OR expansion is not used. All loops in the production compiler traverse AST nodes or bit positions. No satisfying-state list or truth-table synthesis is constructed.

The simplifier does not expand the AST; inputs retain at most 15 nodes/depth 6 and the original width-plus-node budget. The CX guard is tied to the pinned Qiskit decomposition and validated by tests, not claimed to be a universal cost model. Detailed equations and costs are in [MATHEMATICS.md](MATHEMATICS.md).

Symbolic counting remains exact for leaves and conjunctions of bit constraints. General OR counting is deliberately deferred, preserving the established manual-iteration behavior and original tests. No approximate count, hidden domain enumeration, or post-execution classical count feeds iteration selection. The audit found no error in the existing symbolic intersection or first-peak formula.

## Cache and process review

Fingerprints cover canonical AST, width, compiler/schema/cache-format versions and exact Qiskit version. Seed, shots and iteration count do not affect an untranspiled oracle template. Equivalent sibling order is canonicalized; this is not a complete canonical Boolean equivalence solver. Compiler v2/cache-format 2 cannot reuse original v1 entries. Actual expected optimized register counts are checked on load.

New tests cover corrupted payloads, parseable wrong-register/wrong-metadata QPY with a valid digest, failed atomic replace, abandoned temporary files, oversized entries, lowered limits, and fresh-versus-loaded phase equivalence. Existing multiprocess tests still require exactly one miss and three hits for a fresh shared cache. File locking serializes reads/writes/eviction; unique temporary files are fsynced and atomically replaced; failures release locks and remove temporary files. The digest and size cap cover the entire envelope; stale versions are still bounded by eviction.

The Java process bridge was reviewed and left unchanged. It uses fixed argument arrays and structured stdin; 32 KiB input, 256 KiB stdout and 32 KiB stderr limits; independent stream draining/input writing; a deadline; child/descendant destruction; and a semaphore released in finally. Existing tests cover blocked stdin, timeouts/cleanup, slot release, concurrent saturation, nonzero exits, malformed output and both stream caps. Backend stderr remains bounded and is not reflected into public errors. Concurrency is per Java instance, not a distributed service-wide limit. QPY is not assumed safe for hostile files even with a checksum.

## Regression and deployment evidence

The audit's host Maven clean-test run passed all **53 tests**, none skipped, in 28.822 seconds. The Python suite has **26 methods**: all original 15, five independent mathematical/cost audit methods and six cache audit methods. The final host run passed all 26 in 14.539 seconds; the Docker run passed all 26 in 9.717 seconds. The test counts increased through additions; the single existing fixture correction did not remove or relax assertions.

Docker build and startup succeeded with the existing configuration, port 8085, Temurin Java 17.0.20.1, Qiskit 1.1.1 and Aer 0.14.2. All **29 HTTP smoke checks** passed on the final deployment, including legacy routes and Oracle API/Swagger. The original/current reference response field/type shapes match in the HTTP benchmark. The committed browser smoke passed live Aer, recorded before/after costs, nonmatching-parameter behavior, overshooting, AND/OR, desktop/mobile layout, the slashless lab URL, and absence of JavaScript errors. Container logs show actual four-qubit Aer searches and cache hits after recreation. Temporary audit containers were removed; the demo remains running. Existing legacy RANGE defects remain documented in [EXPERIMENTS.md](EXPERIMENTS.md) and untouched. No IBM hardware execution was attempted.

## Remaining risks and limits

The finite generated corpus is not an exhaustive enumeration of all possible valid ASTs. Cost comparisons are all-to-all `u,cx` circuits without device routing, noise or calibration. The fixed cost guard must be rechecked if dependencies or synthesis change. Conservative input budgets can reject a predicate whose simplified circuit would fit; this intentionally preserves validation behavior. General OR cardinality remains manual. The cache trusts its filesystem. Small timing samples and first-request/JIT effects do not establish a durable end-to-end speedup. The pre-audit full server image is local evidence, not a published reproducibility artifact; frozen-core benchmarks are fully reproducible from source.

This extension remains a virtual bit-vector simulator experiment. It does not index arbitrary database records, assume QRAM, demonstrate scientific novelty, or outperform a classical database index.

## Exact audit file inventory

The following paths were changed by this audit, relative to the already-uncommitted feature implementation. The initial worktree's seven tracked-file modifications and its untracked extension files were preserved; `git status` against master therefore includes earlier work too.

Modified existing feature files:

```text
python/oracle/compiler.py
python/oracle/cache.py
python/oracle/executor.py
python/oracle/predicates.py
python/oracle/tests/test_oracle.py
web/src/main/resources/static/oracle-lab/index.html
docs/quantum-oracle/ARCHITECTURE.md
docs/quantum-oracle/MATHEMATICS.md
docs/quantum-oracle/EXPERIMENTS.md
docs/quantum-oracle/REPRODUCIBILITY.md
```

Added audit files:

```text
python/oracle/baseline.py
python/oracle/benchmark.py
python/oracle/http_benchmark.py
python/oracle/tests/audit_reference.py
python/oracle/tests/test_independent_audit.py
python/oracle/tests/test_cache_audit.py
python/oracle/tests/browser_smoke.cjs
web/src/main/resources/static/oracle-lab/audit-reference.json
docs/quantum-oracle/INDEPENDENT_AUDIT.md
docs/quantum-oracle/OPTIMIZATION_RESULTS.md
docs/quantum-oracle/benchmarks/audit-2026-10-31/results.json
docs/quantum-oracle/benchmarks/audit-2026-10-31/pipelines.csv
docs/quantum-oracle/benchmarks/audit-2026-10-31/cache.csv
docs/quantum-oracle/benchmarks/audit-2026-10-31/lab-reference.json
docs/quantum-oracle/benchmarks/audit-2026-10-31/http.json
docs/quantum-oracle/benchmarks/audit-2026-10-31/http.csv
docs/quantum-oracle/benchmarks/audit-2026-10-31/http-initial-localhost.json
docs/quantum-oracle/benchmarks/audit-2026-10-31/http-initial-localhost.csv
```

Local logs, screenshots, initial hashes and smoke responses are under gitignored `verification-results/` or `verification-*.log`. The full retained baseline Docker image is local and was not published. No Java source, Maven configuration, legacy Python script or Docker configuration was edited during the audit.

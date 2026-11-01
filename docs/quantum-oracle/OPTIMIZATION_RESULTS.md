# Audited optimization results

These are real measurements from 2026-10-31. The verdict is **CORRECT WITH LIMITATIONS**. Original and optimized phase oracles were independently verified before timing. Raw data and complete settings are committed in [results.json](benchmarks/audit-2026-10-31/results.json), [pipelines.csv](benchmarks/audit-2026-10-31/pipelines.csv), [cache.csv](benchmarks/audit-2026-10-31/cache.csv), and [http.json](benchmarks/audit-2026-10-31/http.json).

## Conditions

Docker CPU environment: Python 3.10.12, Qiskit 1.1.1, Aer 0.14.2, NumPy 1.26.4; Linux/WSL2 x86_64, 20 reported CPUs. Each Aer process uses one worker thread and a 64 MiB statevector memory limit. Both compilers use basis `u,cx`, optimization level 1, transpiler/simulator seed 42, and 1024 shots. No coupling map, noise, GPU or hardware device is involved. No dependencies or Docker configuration were changed by the audit.

The matrix has 50 width/predicate cases, 151 width/predicate/iteration combinations, two compilers, and three timing repetitions: **906 real Aer samples**. Widths are 2, 3, 4, 5, 6, 8. Cases include equality, partial masks, overlapping AND/OR, nested shared expressions, zero/all solutions, redundancy and the wide disjoint-mask negative candidate. Iterations are 0, 1, 3, with an additional six-iteration reference case. All 151 comparisons had no oracle-only or full-circuit CX increase in the final implementation. This is an empirical matrix result, not a universal optimization guarantee. Compiler timing order uses the separate deterministic shuffle seed 17031.

Core timings use a warm SDK and newly constructed circuits; a pipeline warmup for each compiler is discarded. Compiler order is deterministically shuffled within repetitions. Summaries include min/max and population standard deviation for every numeric metric. Repeated fixed-seed shot counts are not independent statistical samples. Phase correctness is established with statevectors, not with a lucky measurement outcome.

## Reference circuit: width 4, mask 15, value 9, three iterations

| Metric | Original | Optimized |
| --- | ---: | ---: |
| Search / work / total qubits | 4 / 1 / 5 | 4 / 0 / 4 |
| Logical oracle depth | 6 | 3 |
| Transpiled oracle CX / two-qubit gates | 72 | 14 |
| Transpiled oracle depth | 129 | 27 |
| Logical full-Grover depth | 36 | 32 |
| Transpiled full CX / two-qubit gates | 258 | 84 |
| Transpiled full depth | 455 | 158 |
| Amplitude-storage estimate | 512 bytes | 256 bytes |
| Observed valid shots, same seed | 992 / 1024 | 997 / 1024 |
| Exact ideal valid probability | 0.9613189697265625 | 0.9613189697265625 |

The 258 figure is the full Grover circuit after transpilation, not one oracle. The optimized oracle directly marks the four search bits using a three-control MCX conjugated by H. No truth ancilla is needed. At three iterations the remaining diffuser cost is also material. Amplitude bytes exclude the much larger interpreter/SDK/process memory footprint. The different observed shot counts are sampler realization differences, not different oracle semantics or a better theoretical success probability.

| Core time, ms: median [min, max], n=3 | Original | Optimized |
| --- | --- | --- |
| Fresh oracle construction | 0.332 [0.325, 0.335] | 0.224 [0.221, 0.238] |
| Oracle transpilation | 15.101 [14.962, 41.731] | 4.976 [4.915, 5.042] |
| Full-circuit transpilation | 43.477 [43.004, 43.738] | 16.081 [15.500, 16.269] |
| Combined transpilation | 58.839 [58.438, 84.735] | 21.057 [20.541, 21.185] |
| Aer execution | 6.086 [6.041, 6.101] | 2.750 [2.723, 2.851] |
| In-process pipeline total | 68.070 [67.501, 93.890] | 25.534 [24.909, 25.538] |

The in-process total is not REST latency. It excludes interpreter import/startup, JVM/HTTP and transport. The separate independent brute-force evaluator took approximately 0.019 ms for the reference domain; this evaluator intentionally uses per-bit logic and is different from the faster production bitwise verifier. Neither timing should be confused with an optimized classical database index.

## Larger and negative findings

| Width/case, iterations 3 | Oracle CX before/after | Full CX before/after | Full depth before/after |
| --- | --- | --- | --- |
| 8, equality | 1528 / 380 | 5724 / 2280 | 9536 / 3806 |
| 8, partial bitmask | 12 / 1 | 1176 / 1143 | 1904 / 1904 |
| 8, overlapping OR | 40 / 8 | 1260 / 1164 | 1904 / 1904 |
| 8, nested shared predicates | 62 / 49 | 1326 / 1287 | 1904 / 1904 |
| 8, wide disjoint AND | 156 / 145 | 1608 / 1575 | 2351 / 2294 |

The diffuser dominates several width-8 examples: reducing oracle cost did not improve full depth. The wide AND candidate that merged all eight constraints was rejected at 380 oracle CX. The final compiler keeps child truth computations and uses a CZ at the root, yielding 145. Shared-expression bookkeeping can cost more classical construction time: width-8 nested construction was 0.736 ms original versus 0.803 ms optimized, and wide AND 0.557 versus 0.596 ms. Across the matrix, eight of 151 optimized median construction timings were slower. These are retained negative results; no universal runtime gain is claimed.

## Cache measurements

There are **600 cold/warm cache samples**, with three repetitions per width/case/compiler/temperature. Cold means a new application-cache directory in a warm interpreter, not a cold operating system or SDK. Both compilers use the same hardened cache harness; the benchmark privately supplies the original compiler and register expectation, without adding a production compiler selector.

For the reference oracle, median inclusive cache wall time was 6.587 ms original versus 5.813 ms optimized on misses, and 0.331 versus 0.263 ms on hits. Every cold call missed; every warm call hit. Warm compiler time is zero. These sub-millisecond hit differences are small machine-specific observations. Cache-format 2 adds integrity verification, retains atomic writes/locking/bounds, and invalidates original entries through versioned identity. It does not cache transpilation or eliminate process startup.

## End-to-end HTTP measurements

Two private deployments were measured sequentially with identical IPv4 bindings and five requests each, without competing test loads. The first lookup in each empty private cache missed; the next four hit. Response field/type contracts matched exactly. Original v1 and optimized v2 versions are recorded per row.

| HTTP stage, ms | Original | Optimized |
| --- | ---: | ---: |
| Cache-cold server request, n=1 | 1094.664 | 740.238 |
| Cache-warm server median, n=4 | 722.010 | 713.183 |
| Cache-warm server min–max | 700.259–738.626 | 666.861–756.858 |
| Cache-warm server population standard deviation | 14.448 | 33.214 |
| Cache-warm client HTTP median | 732.461 | 728.951 |
| Cache-warm transpilation median | 320.645 | 308.630 |
| Cache-warm Aer execution median | 6.995 | 3.289 |

The approximately 1.2% warm-server median difference is smaller than observed variability. **No robust end-to-end speedup is established.** Interpreter imports and transpiler initialization dominate the server request. The one cache-cold sample per deployment is affected by first-request/JIT effects and cannot support a general cold-latency claim.

The initial localhost run is retained in [http-initial-localhost.json](benchmarks/audit-2026-10-31/http-initial-localhost.json). Its warm server medians were 724.573 ms original and 728.408 ms optimized. Its apparent client advantage was confounded by a roughly two-second connection delay on the original binding. Corrected IPv4 data is reported above; the earlier unfavorable server result was not discarded. The retained original full image is local, not a published artifact; fresh-checkout core/compiler/cache comparisons are reproducible from the frozen baseline source.

## Validation and scope

All original Java interfaces, constructors, request/response DTOs, endpoints and scripts are preserved. General OR still requires manual iterations; only exact existing analytical cardinality is used by normal execution. Validation passed: **53 Java tests**, **26 Python methods on both host and Docker** (including all original 15), **29 final HTTP smoke checks**, Docker build/startup/status, and committed desktop/mobile browser smoke including the measured comparison and both URL forms. No tests were skipped. Details are in [INDEPENDENT_AUDIT.md](INDEPENDENT_AUDIT.md). The deployment remains http://localhost:8085/oracle-lab/ and keeps the original interactive controls and live results. Its compact comparison is recorded benchmark data and is displayed only for matching reference parameters and SDK/compiler versions; it never fabricates a before/after run for another predicate.

This is an optimization of a bounded simulator demonstration. It makes no QRAM assumption, novelty claim, hardware-speed claim or classical-database advantage claim.

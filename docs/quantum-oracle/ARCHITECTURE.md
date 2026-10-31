# Experimental Quantum Oracle Index

This additive extension compiles a Boolean predicate over `x in [0, 2^q-1]` into a reusable Qiskit phase circuit. It never reads a PlankDB collection. It is a logical compiled index over a virtual bit-vector domain, with no QRAM, record encoding, physical database index, or hardware execution.

## Independent execution paths

```text
Legacy: collection -> classical matching -> markedStates -> legacy Grover adapters
Oracle: JSON AST -> validation/canonicalization -> reversible gates -> Grover/Aer
                                                              -> measurements
                                                              -> classical verification
```

Existing Java ports, services, DTOs, collection routes, Python Grover scripts, LOCAL/IBM modes and Swagger configuration retain their interfaces and behavior. No legacy planner or `Strategy` is reused. No IBM account is consulted by this extension.

## Module boundaries

| Module | Additions |
| --- | --- |
| domain | `io.github.swampus.oracle`: immutable predicate/query/report records, compiler/execution ports, typed failure |
| application | `OracleQueryService`: explain/search dispatch, feature flag, resource validation |
| infrastructure | `AerOracleAdapter`, `OracleProcessRunner`, `OracleConfiguration` |
| web | Dedicated Oracle controller, strict request parser, snake_case response, scoped exception advice, static lab |
| python/oracle | Normalizer/counting, reversible compiler, QPY cache, Grover builder, Aer executor and JSON runner |

Dependencies follow the existing domain -> application -> infrastructure -> web module arrangement. The domain has no Qiskit, Jackson or Spring dependency. Java sends structured JSON via standard input to the fixed argument array `[python, -m, oracle.runner, search|explain]`. Clients cannot select a script, executable, file path, or backend. The oracle adapter uses its own ObjectMapper copy; legacy JSON behavior is unchanged.

## API and DSL

`POST /api/rest/v1/oracle/explain` compiles/cache-loads the oracle and transpiles the planned circuit. It does not simulate, enumerate a classical baseline or return measured probabilities.

`POST /api/rest/v1/oracle/search` executes local Aer and independently checks every measured state. After execution, a separate brute-force baseline enumerates the bounded domain for the educational comparison.

```json
{
  "width": 4,
  "predicate": {"type": "BITMASK_EQUALS", "mask": 15, "value": 9},
  "iterations": 3,
  "shots": 1024,
  "seed": 42
}
```

Supported nodes:

```json
{"type":"EQUALS","value":9}
{"type":"BITMASK_EQUALS","mask":3,"value":1}
{"type":"AND","children":[{"type":"BITMASK_EQUALS","mask":3,"value":1},{"type":"BITMASK_EQUALS","mask":12,"value":8}]}
{"type":"OR","children":[{"type":"EQUALS","value":1},{"type":"EQUALS","value":2}]}
```

Constants are unsigned integers fitting `width`. A bitmask value with a bit outside its mask is a well-defined predicate with zero solutions; it is not truncated. Mask zero/value zero is true for all states. Unknown types/fields, fractional/string/boolean numbers, explicit null optional parameters, malformed trees, out-of-width constants and excess resources are rejected. AND/OR have exactly two children. Canonicalization sorts their two children by canonical JSON; it does not perform associative flattening, equivalence solving or algebraic simplification.

The API uses snake_case throughout. It reports the normalized predicate, versioned fingerprint, backend/Qiskit/Aer versions, cache status, transpiled metrics, circuit diagram, timings, and warnings. Search adds counts and probabilities, top state with `valid`, observed valid probability, and a separately timed classical baseline. Theoretical probability is only supplied for analytical cardinality. An OR result can have a classical baseline count while `analytical_solution_count` and theoretical probability remain null: the baseline is intentionally not fed back into execution planning.

Errors share the existing API error envelope: invalid input 400, disabled/saturated simulator 503, timeout 504, backend/protocol errors 502. The lab remains viewable when disabled and explains the 503 response.

## Reusable cache

The SHA-256 key covers canonical predicate, width, schema version, compiler version and exact Qiskit version. Shots, seed and iterations do not change oracle identity. Increment the compiler/schema version when the semantics or circuit representation changes. Old fingerprints cannot be hits; stale files remain eligible for bounded eviction.

Cache entries contain Qiskit-supported QPY circuit templates, never pickle. QPY retains circuit structure and is the documented circuit interchange format ([IBM QPY documentation](https://quantum.cloud.ibm.com/docs/en/api/qiskit/1.0/qpy)). Audit cache format 2 wraps the QPY payload in the eight-byte `PLKQPY2\n` header and a 32-byte SHA-256 digest, verified before QPY loading. The fingerprint also includes the cache-format version. The envelope is internal; use `cache.unpack` to read its QPY payload. Loading independently validates budgets, identity metadata, circuit count and the optimized expected register size. Invalid entries are rebuilt. Compiler v2 and cache format 2 cannot hit the original v1 entries.

The cache is trusted local application storage; clients cannot upload templates. The digest detects accidental corruption, including still-parseable changed gates. It is not an authentication mechanism against a writer who can replace both data and digest. QPY is not treated as a safe parser for attacker-controlled files: protect the cache directory as application data.

A process-wide OS file lock (`flock` on Linux, `msvcrt.locking` on Windows) serializes cache lookup, load, compile, write and LRU eviction. Unique temporary files are flushed/fsynced and atomically replaced. Locks release if a process dies; stale temporary files are removed under the next lock. Limits: 64 entries by default, at most 256; each QPY file at most 1 MiB. Eviction also sees old versions. The lock file is fixed size (zero bytes). Docker uses a named volume so templates survive Python and container restarts. A cache hit reports zero compiler time; cache lookup/load time is reported separately. Transpilation and Grover construction occur per request.

## Resource and subprocess limits

| Setting | Default | Hard ceiling |
| --- | ---: | ---: |
| `QUANTUM_ORACLE_MAX_WIDTH` | 8 | 8 (minimum width 2) |
| `QUANTUM_ORACLE_MAX_TOTAL_QUBITS` | 16 | 16 |
| `QUANTUM_ORACLE_MAX_SHOTS` | 8192 | 8192 |
| `QUANTUM_ORACLE_MAX_ITERATIONS` | 32 | 32 (zero is allowed per query) |
| `QUANTUM_ORACLE_TIMEOUT_SECONDS` | 30 | 120 |
| `QUANTUM_ORACLE_CONCURRENCY` | 2 | 4 per Java instance |
| `QUANTUM_ORACLE_CACHE_ENTRIES` | 64 | 256 |

The AST has at most 15 nodes and depth 6. The original conservative width-plus-nodes budget is checked before circuit allocation and again on cache access. Compiler v2 can use fewer work qubits through direct phases, bounded simplification, removal of the root work bit, and shared subexpressions. Reported qubit counts and `statevector_bytes` use the actual resulting circuit. A 16-qubit complex-double statevector contains 1,048,576 bytes; this describes amplitudes, not Python/Qiskit/process overhead. Aer additionally has a 64 MiB statevector memory limit and one worker thread. Multi-controlled X gates use no hidden ancillary registers; transpilation uses the fixed `u,cx` basis. These conservative limits bound circuit construction too; they are not a general performance guarantee. Multiple service instances would need an external/global concurrency limit.

The oracle controller bounds HTTP JSON to 32 KiB before parsing (including chunked requests), rejects duplicate fields and trailing JSON, and leaves legacy request parsing unchanged. The process runner captures at most 256 KiB stdout and 32 KiB stderr, limits stdin to 32 KiB, drains both streams concurrently, enforces a deadline, destroys descendants/child on termination, closes streams and releases its concurrency slot. Failures do not disclose stderr or secrets. No shell is invoked. The service is a local experiment with no new authentication or public hosting configuration.

## Demo and deployment changes

`/oracle-lab/` uses self-contained HTML/CSS/JavaScript served by Spring, with no frontend build or CDN. A small controller forwards the directory URL to its static index. The browser independently evaluates the predicate for the full bit grid and displays actual server measurements and the Qiskit text circuit. Client HTTP latency is separate from server and Python timing.

Docker retains the Maven Java 17 build and `/app/python`. The removed upstream `openjdk:17-slim` image is replaced with `eclipse-temurin:17-jre-jammy`; Qiskit/Aer/algorithms/runtime pins and legacy scripts are unchanged. Both Compose port examples now use the working 8085 port. `.env.example` is credential-free and enables the optional demo; absent the feature flag it is disabled. The two pre-existing override files remain supported; explicit `-f docker-compose.yml` avoids automatic override warnings.

## Research directions

Potential later work includes symbolic cardinality for OR (inclusion/exclusion or decision diagrams), optimized ancilla reuse, Hamming distance, integer comparators, encoded record attributes, and separate noise experiments. Each needs deterministic oracle equivalence/cleanup tests. Any record-domain work must explicitly account for encoding and loading costs; this implementation makes no efficient QRAM assumption.

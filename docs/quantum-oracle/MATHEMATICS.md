# Reversible predicate compilation and Grover search

## Register convention

For search width `q`, qubits `0..q-1` encode `x = sum_i 2^i x_i`. Qubit zero is the least significant bit. Ancillas occupy the higher-index qubits and start at zero. Only search qubits are measured into classical bits with the same indices. Qiskit count strings are MSB first: `1001` means decimal 9 without reversal. Statevector indices with a nonzero ancilla start at `2^q`.

## Original v1 Boolean circuit (retained comparison baseline)

Each AST node receives a clean target bit. A leaf XORs its truth value into that target using a multi-controlled X gate. For equality, all `q` bits control the gate; for a mask, only selected bits control it. Zero-valued controls are temporarily inverted with X and restored. Mask zero/value zero is an unconditional X; a value outside the mask is constant false and emits no leaf gates. The compiler loops over bit positions and AST nodes, never over domain states or satisfying assignments.

For clean child truth bits `a,b` and clean target `t`:

```text
AND: CCX(a,b,t)                     => t = ab
OR:  CX(a,t); CX(b,t); CCX(a,b,t)    => t = a XOR b XOR ab = a OR b
```

The tree computation `C` stores child results and root truth value in distinct work qubits. The phase oracle is `C† Z_root C` (rightmost operator acts first): compute, apply Z to the root, reverse every computation gate. On the clean-work subspace,

```text
|x>|0...0> -> |x>|work,f(x)> -> (-1)^f(x)|x>|work,f(x)>
          -> (-1)^f(x)|x>|0...0>.
```

This is a unitary reversible implementation for all supported Boolean trees. The promised predicate phase action assumes initially zero work bits, as used by Grover. Work qubits never participate in the diffuser.

## Audited v2 transformations

The original compiler remains verbatim in `python/oracle/baseline.py`. The production compiler now applies bounded, bottom-up Boolean algebra, never truth-table construction. Input validation and its original conservative `width + AST nodes <= 16` budget remain unchanged even when the resulting circuit needs fewer work qubits.

Equality and a feasible mask are cubes: a conjunction of selected bit literals. Temporarily invert zero literals, apply a Z controlled on the remaining selected bits, then restore the inversions. A k-bit cube needs an MCZ on k search bits, rather than computing its truth into a separate bit with MCX, applying Z, and uncomputing. MCZ uses H-MCX-H with k-1 controls. A single-bit cube uses Z directly. False emits identity; true uses global phase pi, exactly `-I`, including on superpositions.

Constants, identical expressions, contradictory intersections, cube subsumption, and adjacent cubes are simplified. The adjacent-union rule is exact: cubes with the same mask and values differing in exactly one constrained bit form the cube obtained by dropping that constraint. There is no general DNF expansion or whole-domain iteration.

Merging constraints is not automatically cheap. The compiler distinguishes a root phase operation from computing an internal truth bit. Under the pinned Qiskit 1.1.1 `noancilla` decomposition, MCX CX counts for 0..8 controls are `[0,1,6,14,36,92,188,380,764]`; a test verifies this table by actual transpilation. An AND merge is accepted only when its isolated CX estimate is no worse than keeping the child computations. For internal computed nodes, compare `CX(MCX_union)` with `CX(MCX_a)+CX(MCX_b)+6`; at the phase root compare `CX(MCZ_union)` with `2*(CX(MCX_a)+CX(MCX_b))+1`. These are selection heuristics under the pinned basis, not a proof of globally optimal synthesis or routed hardware cost.

For a remaining compound root, compute its child truth bits, without allocating a root truth bit. AND phase marking is CZ(a,b). OR phase marking is Z(a) Z(b) CZ(a,b), since `a OR b = a XOR b XOR ab`. Reverse all child computation afterward. Identical subexpressions share a computed work bit through an immutable-expression DAG; they remain available until the phase operation and are all uncomputed in reverse order. Shared controls are never overwritten by phase marking.

For two disjoint root cubes, OR is just the product of their two phase oracles. For overlapping root cubes the phase identity `U_(a OR b) = U_a U_b U_(a AND b)` is used only for small cubes (at most two constrained bits each and three in their intersection). This costs at most eight CX in the pinned decomposition. General expansion can make hardware cost much worse and is deliberately avoided.

The independent audit checks original and optimized circuits against a separate evaluator on every small-domain basis state and uniform superpositions. Signed amplitudes, common global-phase equivalence, exact clean-work support, transpiled circuits, generated redundant/contradictory/overlapping/tautological trees, and Grover dynamics are checked. The original fixture now copies its input before Qiskit evolution: global-phase evolution can mutate an aliased NumPy array, which otherwise corrupted the expected result. No phase or cleanup assertion was relaxed.

## Grover circuit

Apply H to every search qubit, yielding the uniform state `|s>`. Repeat the compiled oracle and search-register diffusion for `k` iterations. Diffusion is implemented with H, X, an MCZ (H-MCX-H), X and H on the search register. This implementation equals the usual `2|s><s|-I` up to an overall minus sign. After `k` repetitions the extra `(-1)^k` is global and cannot change measurement probabilities. No classical sampling replaces Aer execution.

If `N=2^q`, `M` is the number of satisfying states, and `theta=asin(sqrt(M/N))`, the ideal success probability is:

```text
P_valid(k) = sin²((2k+1) theta).
```

For `0<M<N`, the first continuous maximum occurs at `k*=pi/(4 theta)-1/2`. The auto selector evaluates the adjacent nonnegative integer choices using this formula and chooses the higher probability. It rejects a result above the configured iteration budget rather than silently clipping. Explicit integer iterations from 0 through the configured maximum are supported, including intentional overshooting. `M=0` and `M=N` select zero iterations automatically. Zero solutions remain unsuccessful; all-state solutions need no amplification. The result always reports whether the most frequently measured state actually satisfies the predicate.

For a feasible mask, `M=2^(q-popcount(mask))`; equality has `M=1`. Impossible masks have `M=0`. AND of bit constraints merges masks symbolically. A conflict exists when `(value_a XOR value_b) AND mask_a AND mask_b != 0`. It yields zero solutions. Otherwise, the merged mask/value pair gives analytical cardinality. This is constraint algebra, not domain enumeration. OR trees require manual iterations in this version, including when nested under AND unless an impossible conjunct already proves zero. Their theoretical success field remains null even after a verification baseline has counted solutions.

The audit independently exhaustively checked all M in `[0, 2^q]` for widths 2, 3, 4, 5, 6 and 8 against the first-peak iteration rule. Symbolic OR counting was intentionally deferred: changing its required-manual-iterations behavior would change an existing validated contract. No approximate count or post-measurement truth-table count is fed into production planning. The bounded compiler's OR simplifications are independent of cardinality reporting; an OR that simplifies to a constant still retains the original manual-iteration API behavior.

## Verification

Basis-state tests compare every amplitude with the expected signed input, including exact ancilla-zero support. These use a fixed phase convention and therefore make stronger assertions than global-phase equivalence for individual basis states. Separate uniform-superposition tests compare relative phases across satisfying and non-satisfying states, with global equivalence checked as well. This avoids the common error of treating an arbitrary single-state phase as observable evidence of a working oracle.

All equality and mask/value combinations for widths 2 and 3 are checked. Nested AND/OR, contradictory predicates, overlapping OR terms, all-state/zero-state cases, and widths through 8 receive additional tests. Exact Grover statevectors validate the ideal probability for zero/one/multiple/all solutions and overshooting, checking clean work amplitudes at the same time. Shot tests use broad statistical tolerances; fixed seeds additionally check repeatability in the pinned environment. Circuit metrics are evaluated after deterministic transpilation into `u,cx`, including measurements in total depth.

Observed valid probability is the sum of shot counts of all classically verified valid measured states, divided by shots. It is a finite-sample statistic and is distinct from the theoretical probability. Unobserved states have empirical probability zero. The independent full-domain brute-force baseline is timed only after quantum execution, and never feeds compiler or iteration selection.

## Scientific interpretation

For the default `q=4, mask=15, value=9`, `M=1`, and three iterations have ideal success `0.9613189697`. Zero iterations give `0.0625`; six give approximately `0.0203807689`. Grover rotates past the good subspace, so more iterations can make results worse.

The quadratic query-complexity result treats the oracle as a unit-cost primitive. Here actual runtime includes predicate normalization, circuit compilation, construction, transpilation, subprocess startup, classical statevector simulation and verification. The compiled representation can be reused, but no claim is made that this simulator beats a direct predicate test, HashMap, B-tree or PostgreSQL. The classical baseline is an explicitly linear educational comparison, not an optimized database benchmark.

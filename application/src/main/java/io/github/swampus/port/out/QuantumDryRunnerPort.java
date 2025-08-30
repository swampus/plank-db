package io.github.swampus.port.out;

import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QuantumPlan;

/**
 * Secondary (driven) port for performing a local quantum dry-run
 * for the Explain endpoint. Implementations are expected to run
 * a Python script (Aer simulator) and return probabilities.
 * <p>
 * Contract:
 * - For LOCAL backend: execute and return DryRunResult
 * - For IBM backend: return null (Explain is plan-only on IBM)
 * - On failures: either throw an AppException-like error or return null,
 * depending on the desired UX (we usually return null to not block Explain).
 */
public interface QuantumDryRunnerPort {
    DryRunResult dryRun(QuantumPlan plan);
}

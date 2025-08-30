package io.github.swampus.model;

import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QuantumPlan;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExplainPlanModel {
    private QuantumPlan plan;
    private DryRunResult dryRun;
}

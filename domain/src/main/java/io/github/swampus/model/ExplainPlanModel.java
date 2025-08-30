package io.github.swampus.model;

import io.github.swampus.quntum.DryRunResult;
import io.github.swampus.quntum.QuantumPlan;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExplainPlanModel {
    private QuantumPlan plan;
    private DryRunResult dryRun;
}

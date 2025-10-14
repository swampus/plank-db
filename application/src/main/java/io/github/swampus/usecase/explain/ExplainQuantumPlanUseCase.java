package io.github.swampus.usecase.explain;

import io.github.swampus.model.ExplainPlanModel;

public interface ExplainQuantumPlanUseCase {
    ExplainPlanModel execute(String collection, ExplainPlanInput input);
}

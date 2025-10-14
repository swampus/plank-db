package io.github.swampus.controller;

import io.github.swampus.exception.QuantumInvalidRequestException;
import io.github.swampus.model.ExplainPlanModel;
import io.github.swampus.quantum.BackendInfo;
import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QuantumPlan;
import io.github.swampus.quantum.QueryMode;
import io.github.swampus.usecase.explain.ExplainQuantumPlanUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExplainPlanController.class)
@TestPropertySource(properties = {
        "spring.jackson.property-naming-strategy=SNAKE_CASE"
})
class ExplainControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ExplainQuantumPlanUseCase useCase;

    @Test
    void key_local_with_dryrun_200() throws Exception {
        var plan = new QuantumPlan(
                QueryMode.KEY, "k2",
                Map.of("k1","00","k2","01"),
                List.of("01"),
                1, 2, 2, 1, 1, "(x01)", 4,                           // ← "(x01)"
                Map.of(),
                new BackendInfo("local/aer_simulator", 512, 42, false), // ← local
                List.of(),
                "e961c048b95e"
        );
        var dry = new DryRunResult(
                "01", Map.of("01", 0.86), 0.86, 12L,
                null, null, null, null
        );
        when(useCase.execute(eq("test"), any()))
                .thenReturn(new ExplainPlanModel(plan, dry));

        mvc.perform(post("/api/rest/v1/collections/test/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                      {"mode":"KEY","key":"k2","backend":"local","shots":512,"seed":42}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.target_label").value("k2"))
                .andExpect(jsonPath("$.plan.backend.name").value("local/aer_simulator"))
                .andExpect(jsonPath("$.dry_run.top_measurement").value("01"));
    }

    @Test
    void ibm_plan_only_dryrun_null() throws Exception {
        var plan = new QuantumPlan(
                QueryMode.KEY, "k3",
                Map.of("k1","00","k3","10"),
                List.of("10"),
                1, 2, 2, 1, 1, "(x10)", 4,
                Map.of(),
                new BackendInfo("ibm/runtime", 2048, null, true),
                List.of(),
                "e961c048b95e" // <= planId (любой String)
        );
        when(useCase.execute(eq("test"), any())).thenReturn(new ExplainPlanModel(plan, null));

        mvc.perform(post("/api/rest/v1/collections/test/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"KEY","key":"k3","backend":"ibm"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dry_run").doesNotExist());
    }

    @Test
    void bad_request_400() throws Exception {
        doThrow(new QuantumInvalidRequestException("key must be provided"))
                .when(useCase).execute(eq("test"), any());

        mvc.perform(post("/api/rest/v1/collections/test/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"KEY","backend":"local"}
                                """))
                .andExpect(status().isBadRequest());
    }
}

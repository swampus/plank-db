package io.github.swampus.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.swampus.exception.KeyNotFoundException;
import io.github.swampus.exception.QuantumIllegalStateException;
import io.github.swampus.exception.QuantumInvalidRequestException;
import io.github.swampus.exception.RangeNotFoundException;
import io.github.swampus.model.ExplainPlanModel;
import io.github.swampus.port.out.CollectionReaderPort;
import io.github.swampus.port.out.QuantumDryRunnerPort;
import io.github.swampus.port.out.QuantumDryRunnerPort.DryRunOptions;
import io.github.swampus.quantum.DryRunResult;
import io.github.swampus.quantum.QueryMode;
import io.github.swampus.usecase.explain.ExplainPlanInput;
import io.github.swampus.usecase.explain.Strategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ExplainQuantumPlanServiceTest {

    @Mock
    CollectionReaderPort collectionReader;

    @Mock
    QuantumDryRunnerPort dryRunner;

    @Captor
    ArgumentCaptor<QuantumDryRunnerPort.DryRunOptions> optsCaptor;

    @Captor
    ArgumentCaptor<io.github.swampus.quantum.QuantumPlan> planCaptor;

    ExplainQuantumPlanService service;

    @BeforeEach
    void setUp() {
        service = new ExplainQuantumPlanService(collectionReader, dryRunner);
    }

    @Test
    void key_local_with_dryrun_success() {
        // Given a deterministic tiny collection
        when(collectionReader.getAllEntries("test"))
                .thenReturn(Map.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"));

        // And dry-run returns some histogram (8-field ctor per your record)
        DryRunResult dry = new DryRunResult(
                "01",
                Map.of("01", 0.86, "11", 0.14),
                0.86,
                12L,
                null, // probabilitiesNoisy
                null, // topK
                null, // circuitPngB64
                null  // histogramPngB64
        );
        when(dryRunner.dryRun(any(), any())).thenReturn(dry);

        // When calling KEY search for k2 on local with render/noise/topK
        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k2")
                .backend("local")
                .strategy(Strategy.AUTO)
                .shots(512)
                .seed(42)
                .render(true)
                .noise(0.05) // should pass through unclamped
                .topK(2)
                .build();

        ExplainPlanModel model = service.execute("test", input);

        // Then the plan is built
        assertNotNull(model);
        assertNotNull(model.getPlan());
        assertNotNull(model.getDryRun());

        // Check key fields
        var plan = model.getPlan();
        assertEquals(QueryMode.KEY, plan.mode());
        assertEquals("k2", plan.targetLabel());
        assertEquals(4, plan.collectionSizeN());
        assertEquals(2, plan.numQubits()); // ceil(log2(4)) = 2

        // Encoding is lexicographic: k1->00, k2->01, k3->10, k4->11
        assertEquals("01", plan.encodingMap().get("k2"));
        assertEquals(List.of("01"), plan.markedStates());

        // Iterations AUTO for N=4, M=1 -> floor(pi/4*sqrt(4)) = 1
        assertEquals(1, plan.optimalIterations());
        assertEquals(1, plan.iterationsUsed());

        // PlanId present and looks like 12-hex
        assertNotNull(plan.planId());
        assertTrue(plan.planId().matches("[0-9a-f]{12}"), "planId should be 12 hex chars");

        // Dry-run returned as-is
        assertEquals("01", model.getDryRun().topMeasurement());
        assertEquals(0.86, model.getDryRun().confidenceScore(), 1e-9);

        // And we passed proper DryRunOptions to adapter
        verify(dryRunner).dryRun(planCaptor.capture(), optsCaptor.capture());
        DryRunOptions opts = optsCaptor.getValue();
        assertTrue(opts.render());
        assertEquals(0.05, opts.noise(), 1e-9);
        assertEquals(2, opts.topK());
    }

    @Test
    void ibm_backend_returns_plan_only_no_dryrun() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("a", "1", "b", "2"));

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("a")
                .backend("ibm") // IBM: no dry-run
                .build();

        ExplainPlanModel model = service.execute("c", input);

        assertNotNull(model.getPlan());
        assertNull(model.getDryRun());
        // dryRunner must NOT be called
        verify(dryRunner, never()).dryRun(any(), any());
    }

    @Test
    void range_local_marks_correct_states() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"));

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.RANGE)
                .fromKey("k2")
                .toKey("k4")
                .backend("local")
                .build();

        // Adapter can return null (simulate safe fallback)
        when(dryRunner.dryRun(any(), any())).thenReturn(null);

        ExplainPlanModel model = service.execute("c", input);

        var plan = model.getPlan();
        // In range k2..k4 -> states 01,10,11
        assertEquals(List.of("01", "10", "11"), plan.markedStates());
        assertEquals(3, plan.estimatedM());
        assertEquals("(x01 | x10 | x11)".replace(" ", ""), plan.oracleExpression().replace(" ", ""));
    }

    @Test
    void strategy_fixed_uses_given_iterations() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"));

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k3")
                .backend("local")
                .strategy(Strategy.FIXED)
                .iterations(3)
                .build();

        when(dryRunner.dryRun(any(), any())).thenReturn(null);

        var model = service.execute("c", input);
        assertEquals(3, model.getPlan().optimalIterations());
        assertEquals(3, model.getPlan().iterationsUsed());
    }

    @Test
    void invalid_key_in_key_mode_throws_400_like_exception() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("k1", "v1"));

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("") // invalid
                .backend("local")
                .build();

        assertThrows(QuantumInvalidRequestException.class, () -> service.execute("c", input));
    }

    @Test
    void key_not_found_throws() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("k1", "v1"));

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k2") // absent
                .backend("local")
                .build();

        assertThrows(KeyNotFoundException.class, () -> service.execute("c", input));
    }

    @Test
    void range_without_hits_throws() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("a", "1", "b", "2"));

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.RANGE)
                .fromKey("x")
                .toKey("z")
                .backend("local")
                .build();

        assertThrows(RangeNotFoundException.class, () -> service.execute("c", input));
    }

    @Test
    void empty_collection_throws() {
        when(collectionReader.getAllEntries("empty"))
                .thenReturn(Map.of());

        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k1")
                .backend("local")
                .build();

        assertThrows(QuantumIllegalStateException.class, () -> service.execute("empty", input));
    }

    @Test
    void collection_null_throws_npe() {
        ExplainPlanInput input = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k1")
                .backend("local")
                .build();

        assertThrows(NullPointerException.class, () -> service.execute(null, input));
    }

    @Test
    void planId_is_deterministic_for_same_inputs_and_changes_on_shots() {
        when(collectionReader.getAllEntries("c"))
                .thenReturn(Map.of("k1", "v1", "k2", "v2"));

        ExplainPlanInput base = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k1")
                .backend("local")
                .strategy(Strategy.AUTO)
                .shots(256)
                .seed(7)
                .build();

        var m1 = service.execute("c", base);
        var m2 = service.execute("c", base);
        assertEquals(m1.getPlan().planId(), m2.getPlan().planId(), "same inputs → same planId");

        // change shots → different planId (shots included in backend metadata)
        ExplainPlanInput changed = ExplainPlanInput.builder()
                .mode(QueryMode.KEY)
                .key("k1")
                .backend("local")
                .strategy(Strategy.AUTO)
                .shots(1024)
                .seed(7)
                .build();
        var m3 = service.execute("c", changed);
        assertNotEquals(m1.getPlan().planId(), m3.getPlan().planId(), "changing shots should change planId");
    }
}

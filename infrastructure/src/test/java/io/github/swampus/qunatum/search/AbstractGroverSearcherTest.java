package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.exception.QuantumInvalidInputException;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.qunatum.QuantumProcessRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractGroverSearcherTest {

    private QuantumProcessRunner runner;
    private QuantumConfig config;
    private ObjectMapper mapper;
    private AbstractGroverSearcher searcher;

    @BeforeEach
    void setUp() {
        runner = mock(QuantumProcessRunner.class);
        config = new QuantumConfig();
        config.setQuantumExecutionMode(io.github.swampus.config.ExecutionMode.LOCAL);
        config.setLocalScriptPath("grover.py");

        mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        searcher = new AbstractGroverSearcher(runner, mapper, config) {
            @Override
            protected List<String> buildArgs(String key, String json, String entriesJson) {
                return List.of();
            }

            @Override
            protected String getResolvedScriptPath(boolean isRange) {
                return config.getLocalScriptPath();
            }

        };
    }

    @Test
    void testSearch_returnsValidModel() {
        String key = "k2";
        Set<String> keys = Set.of("k1", "k2");

        String json = """
                {
                  "quantum_result": {
                    "matched_key": "k2",
                    "matched_value": "value_for_k2",
                    "matched_index": 1,
                    "top_measurement": "01",
                    "oracle_expression": "(x01)",
                    "num_qubits": 2,
                    "probabilities": { "01": 0.87, "11": 0.13 },
                    "confidence_score": 0.87,
                    "execution_time_ms": 123,
                    "oracle_depth": 1,
                    "iterations": 1,
                    "note": "Success"
                  },
                  "scientific_notes": {
                    "principle": "Grover",
                    "theory": "Search theory",
                    "circuit_behavior": "Behavior",
                    "confidence_interpretation": "High",
                    "qubit_commentary": "Used 2",
                    "encoding_map": { "k1": "00", "k2": "01" },
                    "used_iterations": 1
                  }
                }
                """;

        when(runner.run(anyString(), anyList())).thenReturn(json);

        QuantumResultModel result = searcher.search(key, keys);

        assertNotNull(result.getQuantumResult());
        assertEquals("k2", result.getQuantumResult().getMatchedKey());
        assertEquals(0.87, result.getQuantumResult().getConfidenceScore());
    }

    @Test
    void testSearch_throwsOnInvalidJson() {
        when(runner.run(anyString(), anyList())).thenReturn("this is not JSON");

        assertThrows(QuantumInvalidInputException.class, () ->
                searcher.search("k1", Set.of("k1", "k2")));
    }

}

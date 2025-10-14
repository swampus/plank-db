package io.github.swampus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.model.QuantumResultModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureJsonTesters
@SpringBootTest
class QuantumJsonTest {

    @Autowired
    ObjectMapper mapper;

    @Test
    void parse_withoutNoisy_ok() throws Exception {
        String json = """
    {
      "quantum_result": {
        "probabilities": {"00":1},
        "iterations":1,
        "note": null,
        "matched_key":"k4",
        "matched_value":"value_for_k4",
        "matched_index":0,
        "top_measurement":"00",
        "oracle_expression":"(x11)",
        "num_qubits":2,
        "confidence_score":1,
        "execution_time_ms":14125,
        "oracle_depth":1
      },
      "scientific_notes": {
        "principle":"...",
        "theory":"...",
        "circuit_behavior":"...",
        "confidence_interpretation":"...",
        "qubit_commentary": null,
        "encoding_map":{"k4":"00","k3":"01","k2":"10","k1":"11"},
        "used_iterations":1
      }
    }""";

        QuantumResultModel m = mapper.readValue(json, QuantumResultModel.class);
        assertEquals("00", m.getQuantumResult().getTopMeasurement());
        assertNull(m.getQuantumResult().getProbabilitiesNoisy());
    }

    @Test
    void parse_withNoisy_ok() throws Exception {
        String json = """
    {"quantum_result":{"probabilities":{"00":1},"probabilities_noisy":{"00":0.9},"iterations":1}}
    """;
        QuantumResultModel m = mapper.readValue(json, QuantumResultModel.class);
        assertNotNull(m.getQuantumResult().getProbabilitiesNoisy());
    }
}


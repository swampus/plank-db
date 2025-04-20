package io.github.swampus.mapper;

import io.github.swampus.dto.QuantumResultDTO;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.model.QuantumResultModel.QuantumResult;
import io.github.swampus.model.QuantumResultModel.ScientificNotes;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
class QuantumResultMapperTest {

    private final QuantumResultMapper mapper = new QuantumResultMapper();

    @Test
    void testToDto_shouldMapCorrectly() {
        QuantumResult result = getQuantumResult();

        ScientificNotes notes = new ScientificNotes();
        notes.setPrinciple("Grover");
        notes.setTheory("Amplification");
        notes.setCircuitBehavior("Flip");
        notes.setConfidenceInterpretation("Good");
        notes.setQubitCommentary("Used 2");
        notes.setEncodingMap(Map.of("k1", "00"));
        notes.setUsedIterations(1);

        QuantumResultModel model = new QuantumResultModel();
        model.setQuantumResult(result);
        model.setScientificNotes(notes);

        QuantumResultDTO dto = mapper.toDto(model);

        assertThat(dto.getQuantumResult().getMatchedKey()).isEqualTo("k1");
        assertThat(dto.getQuantumResult().getConfidenceScore()).isEqualTo(0.9);
        assertThat(dto.getScientificNotes().getTheory()).isEqualTo("Amplification");
        assertThat(dto.getScientificNotes().getEncodingMap()).containsEntry("k1", "00");
    }

    private static QuantumResult getQuantumResult() {
        QuantumResult result = new QuantumResult();
        result.setMatchedKey("k1");
        result.setMatchedValue("v1");
        result.setMatchedIndex(0);
        result.setTopMeasurement("00");
        result.setOracleExpression("(x00)");
        result.setNumQubits(2);
        result.setProbabilities(Map.of("00", 0.9, "01", 0.1));
        result.setConfidenceScore(0.9);
        result.setExecutionTimeMs(123);
        result.setOracleDepth(1);
        result.setIterations(1);
        result.setNote("Success");
        return result;
    }
}
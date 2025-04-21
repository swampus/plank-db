package io.github.swampus.mapper;

import io.github.swampus.dto.QuantumResultDTO;
import io.github.swampus.model.QuantumResultModel;
import org.springframework.stereotype.Component;

@Component
public class QuantumResultMapper {

    public QuantumResultDTO toDto(QuantumResultModel model) {
        QuantumResultDTO dto = new QuantumResultDTO();

        var dtoQr = getQuantumResult(model);
        dto.setQuantumResult(dtoQr);

        var modelSn = model.getScientificNotes();
        var dtoSn = new QuantumResultDTO.ScientificNotes();
        dtoSn.setPrinciple(modelSn.getPrinciple());
        dtoSn.setTheory(modelSn.getTheory());
        dtoSn.setCircuitBehavior(modelSn.getCircuitBehavior());
        dtoSn.setConfidenceInterpretation(modelSn.getConfidenceInterpretation());
        dtoSn.setQubitCommentary(modelSn.getQubitCommentary());
        dtoSn.setEncodingMap(modelSn.getEncodingMap());
        dtoSn.setUsedIterations(modelSn.getUsedIterations());
        dto.setScientificNotes(dtoSn);

        return dto;
    }

    private static QuantumResultDTO.QuantumResult getQuantumResult(QuantumResultModel model) {
        var modelQr = model.getQuantumResult();
        var dtoQr = new QuantumResultDTO.QuantumResult();
        dtoQr.setMatchedKey(modelQr.getMatchedKey());
        dtoQr.setMatchedValue(modelQr.getMatchedValue());
        dtoQr.setMatchedIndex(modelQr.getMatchedIndex());
        dtoQr.setTopMeasurement(modelQr.getTopMeasurement());
        dtoQr.setOracleExpression(modelQr.getOracleExpression());
        dtoQr.setNumQubits(modelQr.getNumQubits());
        dtoQr.setProbabilities(modelQr.getProbabilities());
        dtoQr.setConfidenceScore(modelQr.getConfidenceScore());
        dtoQr.setExecutionTimeMs(modelQr.getExecutionTimeMs());
        dtoQr.setOracleDepth(modelQr.getOracleDepth());
        dtoQr.setIterations(modelQr.getIterations());
        dtoQr.setNote(modelQr.getNote());
        return dtoQr;
    }
}

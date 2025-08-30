package io.github.swampus.quantum.search.ibm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.quantum.QuantumProcessRunner;
import io.github.swampus.quantum.search.AbstractGroverSearcher;

import java.util.List;


public class GroverIbmSearcher extends AbstractGroverSearcher {

    public GroverIbmSearcher(QuantumProcessRunner runner, ObjectMapper objectMapper, QuantumConfig config) {
        super(runner, objectMapper, config);
    }

    @Override
    protected List<String> buildArgs(String key, String keysJson, String entriesJson) {
        return List.of(
                key,
                keysJson,
                entriesJson,
                "--backend=ibm",
                "--ibm-backend=ibm_brisbane"
        );
    }

    @Override
    protected String getResolvedScriptPath(boolean isRange) {
        return getConfig().getIbmScriptPath();
    }
}
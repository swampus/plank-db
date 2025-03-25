package io.github.swampus.quantum.db.internal;

import io.github.swampus.quantum.db.config.QuantumConfig;

import java.util.ArrayList;
import java.util.List;

public class GroverIbmSearcher extends AbstractGroverSearcher {

    public GroverIbmSearcher(QuantumConfig config) {
        super(config);
    }

    @Override
    public String search(String key, List<String> keys) {
        try {
            String jsonKeys = mapper.writeValueAsString(keys);
            List<String> command = new ArrayList<>();
            command.add(config.getPythonExecutable());
            command.add(config.getIbmScriptPath());
            command.add(config.getIbmToken());
            command.add(key);
            command.add(jsonKeys);
            return runPythonScript(command);
        } catch (Exception e) {
            logger.warning("Serialization error: " + e.getMessage());
            return "NOT_FOUND";
        }
    }
}
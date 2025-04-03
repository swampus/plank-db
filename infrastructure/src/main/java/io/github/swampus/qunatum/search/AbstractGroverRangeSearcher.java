package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.ports.QuantumRangeSearcher;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.Set;

@AllArgsConstructor
public abstract class AbstractGroverRangeSearcher implements QuantumRangeSearcher {

    private ObjectMapper objectMapper;
    private QuantumConfig quantumConfig;
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<String> searchInRange(Set<String> keys, String fromKey, String toKey) {
        try {
            String keysJson = objectMapper.writeValueAsString(keys);
            String[] command = buildCommand(fromKey, toKey, keysJson);
            Process process = new ProcessBuilder(command).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info(getClass().getSimpleName() + " Output: " + line);
                    if (!line.isBlank() && !line.startsWith("DEBUG")) {
                        return Optional.of(line);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error during quantum range search", e);
        }
        return Optional.empty();
    }

    protected abstract String[] buildCommand(String fromKey, String toKey, String keysJson);

    public QuantumConfig getQuantumConfig() {
        return quantumConfig;
    }
}

package io.github.swampus.search.python;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.config.QuantumProperties;
import io.github.swampus.ports.QuantumSearcher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractPythonSearcher {

    protected final QuantumConfig quantumConfig;
    protected final QuantumProperties properties;
    protected final ObjectMapper objectMapper;
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public String search(String key, List<String> keys) {
        try {
            String keysJson = objectMapper.writeValueAsString(keys);
            String[] command = buildCommand(quantumConfig, properties, key, keysJson);
            Process process = new ProcessBuilder(command).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                String lastLine = null;
                while ((line = reader.readLine()) != null) {
                    logger.info(getClass().getSimpleName() + " Output: {}", line);
                    lastLine = line;
                }
                return lastLine;
            }
        } catch (Exception e) {
            logger.error("Error during quantum search", e);
            return null;
        }
    }

    protected abstract String[] buildCommand(
            QuantumConfig quantumConfig,
            QuantumProperties properties,
            String key,
            String keysJson);
}


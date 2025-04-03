package io.github.swampus.qunatum.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.config.QuantumConfig;
import io.github.swampus.ports.QuantumSearcher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public abstract class AbstractGroverSearcher implements QuantumSearcher {

    private final QuantumConfig config;
    private final ObjectMapper objectMapper;
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public String search(String key, Set<String> keys) {
        try {
            String keysJson = objectMapper.writeValueAsString(keys);
            String[] command = buildCommand(key, keysJson);
            System.out.println("\n \n Execute command: " + Arrays.toString(command));
            Process process = new ProcessBuilder(command).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                String lastLine = null;
                while ((line = reader.readLine()) != null) {
                    logger.info(getClass().getSimpleName() + " Output: " + line);
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
            String key,
            String keysJson);

    public QuantumConfig getConfig() {
        return config;
    }
}


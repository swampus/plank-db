package io.github.swampus.quantum.db.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.quantum.db.config.QuantumConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractGroverSearcher implements QuantumSearcher {
    protected final QuantumConfig config;
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final Logger logger = Logger.getLogger(this.getClass().getName());

    protected AbstractGroverSearcher(QuantumConfig config) {
        this.config = config;
    }

    protected String runPythonScript(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();

            logger.info(getClass().getSimpleName() + " Output:\n" + (line != null ? line.trim() : ""));
            return line != null ? line.trim() : "NOT_FOUND";
        } catch (Exception e) {
            logger.warning(getClass().getSimpleName() + " execution failed: " + e.getMessage());
            return "NOT_FOUND";
        }
    }
}


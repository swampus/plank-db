package io.github.swampus.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;


@ConfigurationProperties(prefix = "quantum")
@Getter
@Setter
public class QuantumConfig {

    private String pythonExecutable;
    private String quantumIbmToken;
    private String ibmScriptPath;
    private String localScriptPath;
    private String ibmRangeScriptPath;
    private String localRangeScriptPath;
    private ExecutionMode quantumExecutionMode;

    @PostConstruct
    public void validate() {
        if (isNullOrBlank(pythonExecutable)) {
            throw new IllegalStateException("QUANTUM_PYTHON_EXEC is not set.");
        }
        if (quantumExecutionMode == null) {
            throw new IllegalStateException("QUANTUM_EXECUTION_MODE is not set.");
        }
        switch (quantumExecutionMode) {
            case LOCAL -> {
                check("QUANTUM_LOCAL_SCRIPT_PATH", localScriptPath);
                check("QUANTUM_LOCAL_RANGE_SCRIPT_PATH", localRangeScriptPath);
            }
            case IBM -> {
                check("QUANTUM_IBM_SCRIPT_PATH", ibmScriptPath);
                check("QUANTUM_IBM_RANGE_SCRIPT_PATH", ibmRangeScriptPath);
                check("QUANTUM_IBM_TOKEN", quantumIbmToken);
            }
        }
    }

    private void check(String name, String value) {
        if (isNullOrBlank(value)) {
            throw new IllegalStateException(name + " is not set.");
        }
    }

    private boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

}

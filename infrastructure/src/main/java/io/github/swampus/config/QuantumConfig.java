package io.github.swampus.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "quantum")
@Data
public class QuantumConfig {

    @PostConstruct
    public void logLoaded() {
        System.out.println("QuantumConfig loaded: " + this);
    }
    private String pythonExecutable;
    private String ibmToken;
    private String ibmScriptPath;
    private String localScriptPath;
    private String ibmRangeScriptPath;
    private String localRangeScriptPath;
    private ExecutionMode executionMode;

}

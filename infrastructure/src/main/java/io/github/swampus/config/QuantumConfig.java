package io.github.swampus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "quantum")
@Data
public class QuantumConfig {
    private String pythonExecutable;
    private String ibmToken;
    private String ibmScriptPath;
    private String localScriptPath;
    private ExecutionMode mode;

}

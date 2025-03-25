package io.github.swampus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "quantum")
public class QuantumProperties {

    private ExecutionMode mode = ExecutionMode.LOCAL;
    private String pythonExecutable;
    private String groverLocalScript;
    private String groverIbmScript;
    private String ibmToken;

    public ExecutionMode getMode() {
        return mode;
    }

    public void setMode(ExecutionMode mode) {
        this.mode = mode;
    }

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public String getGroverLocalScript() {
        return groverLocalScript;
    }

    public void setGroverLocalScript(String groverLocalScript) {
        this.groverLocalScript = groverLocalScript;
    }

    public String getGroverIbmScript() {
        return groverIbmScript;
    }

    public void setGroverIbmScript(String groverIbmScript) {
        this.groverIbmScript = groverIbmScript;
    }

    public String getIbmToken() {
        return ibmToken;
    }

    public void setIbmToken(String ibmToken) {
        this.ibmToken = ibmToken;
    }
}

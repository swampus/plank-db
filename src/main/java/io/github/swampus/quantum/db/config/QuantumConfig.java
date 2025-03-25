package io.github.swampus.quantum.db.config;

public class QuantumConfig {

    private String backend = "qasm_simulator";
    private int shots = 1024;
    private boolean useLocalPython = true;
    private String localScriptPath = "python/grover.py";
    private String ibmScriptPath = "python/grover_ibm.py";
    private String pythonExecutable = "python";
    private String ibmToken;
    private ExecutionMode mode = ExecutionMode.LOCAL;

    public enum ExecutionMode {
        LOCAL, IBM
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getShots() {
        return shots;
    }

    public void setShots(int shots) {
        this.shots = shots;
    }

    public boolean isUseLocalPython() {
        return useLocalPython;
    }

    public void setUseLocalPython(boolean useLocalPython) {
        this.useLocalPython = useLocalPython;
    }

    public String getLocalScriptPath() {
        return localScriptPath;
    }

    public void setLocalScriptPath(String localScriptPath) {
        this.localScriptPath = localScriptPath;
    }

    public String getIbmScriptPath() {
        return ibmScriptPath;
    }

    public void setIbmScriptPath(String ibmScriptPath) {
        this.ibmScriptPath = ibmScriptPath;
    }

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public String getIbmToken() {
        return ibmToken;
    }

    public void setIbmToken(String ibmToken) {
        this.ibmToken = ibmToken;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public void setMode(ExecutionMode mode) {
        this.mode = mode;
    }
}
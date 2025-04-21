package io.github.swampus.ports;

import java.util.List;

public interface QuantumScriptExecutor {
    String run(String script, List<String> args);
}

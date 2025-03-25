package io.github.swampus.config;

import java.util.concurrent.atomic.AtomicReference;

public class QuantumRuntimeConfig {
    private final AtomicReference<ExecutionMode> currentMode =
            new AtomicReference<>(ExecutionMode.LOCAL);

    public ExecutionMode getMode() {
        return currentMode.get();
    }

    public void setMode(ExecutionMode mode) {
        currentMode.set(mode);
    }
}

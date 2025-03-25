package io.github.swampus.quantum.db.factory;

import io.github.swampus.quantum.db.config.QuantumConfig;
import io.github.swampus.quantum.db.internal.GroverIbmSearcher;
import io.github.swampus.quantum.db.internal.GroverLocalSearcher;
import io.github.swampus.quantum.db.internal.QuantumSearcher;

public class QuantumSearcherFactory {

    public static QuantumSearcher create(QuantumConfig config) {
        switch (config.getMode()) {
            case LOCAL:
                return new GroverLocalSearcher(config);
            case IBM:
                return new GroverIbmSearcher(config);
            default:
                throw new IllegalArgumentException("Unsupported mode: " + config.getMode());
        }
    }
}


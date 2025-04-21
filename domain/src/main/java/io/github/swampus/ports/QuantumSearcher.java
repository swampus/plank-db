package io.github.swampus.ports;

import io.github.swampus.model.QuantumResultModel;

import java.util.Set;

public interface QuantumSearcher {
    QuantumResultModel search(String key, Set<String> keys);
}

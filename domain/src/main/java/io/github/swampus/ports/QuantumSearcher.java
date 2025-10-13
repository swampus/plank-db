package io.github.swampus.ports;

import io.github.swampus.model.QuantumResultModel;

import java.util.List;

public interface QuantumSearcher {
    QuantumResultModel search(String key, List<String> keys);
}

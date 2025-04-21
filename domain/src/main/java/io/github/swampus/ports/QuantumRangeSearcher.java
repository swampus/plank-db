package io.github.swampus.ports;

import io.github.swampus.model.QuantumResultModel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuantumRangeSearcher {
    QuantumResultModel searchInRange(Set<String> keys, String fromKey, String toKey);
}

package io.github.swampus.ports;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuantumRangeSearcher {
    Optional<String> searchInRange(Set<String> keys, String fromKey, String toKey);
}

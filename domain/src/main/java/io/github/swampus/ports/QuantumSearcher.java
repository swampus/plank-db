package io.github.swampus.ports;

import java.util.List;
import java.util.Set;

public interface QuantumSearcher {
    String search(String key, Set<String> keys);
}

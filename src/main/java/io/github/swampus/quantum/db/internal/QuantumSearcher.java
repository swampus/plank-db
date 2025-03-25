package io.github.swampus.quantum.db.internal;

import java.util.List;

public interface QuantumSearcher {
    String search(String key, List<String> keys);
}

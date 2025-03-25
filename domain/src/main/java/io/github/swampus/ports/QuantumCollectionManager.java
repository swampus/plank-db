package io.github.swampus.ports;

import java.util.List;

public interface QuantumCollectionManager {

    void put(String collection, String key, String value);

    String get(String collection, String key);

    void delete(String collection, String key);

    List<String> getKeys(String collection);

    boolean containsKey(String collection, String key);

    List<String> rangeQuery(String collection, String startKey, String endKey);
}

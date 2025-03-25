package io.github.swampus.quantum.db;

import io.github.swampus.quantum.db.config.QuantumConfig;
import io.github.swampus.quantum.db.factory.QuantumSearcherFactory;
import io.github.swampus.quantum.db.internal.QuantumSearcher;

import java.util.*;
import java.util.stream.Collectors;

public class QuantumMap<K extends String, V> implements Map<K, V> {

    private final Map<K, V> classicalStorage = new HashMap<>();
    private final QuantumSearcher searcher;

    public QuantumMap() {
        QuantumConfig config = new QuantumConfig();
        this.searcher = QuantumSearcherFactory.create(config);
    }

    public QuantumMap(QuantumConfig config) {
        this.searcher = QuantumSearcherFactory.create(config);
    }

    @Override
    public V get(Object key) {
        List<String> keyList = classicalStorage.keySet()
                .stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        String result = searcher.search(String.valueOf(key), keyList);
        if (!"NOT_FOUND".equals(result)) {
            return classicalStorage.get(result);
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        return classicalStorage.put(key, value);
    }

    @Override
    public int size() {
        return classicalStorage.size();
    }

    @Override
    public boolean isEmpty() {
        return classicalStorage.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return classicalStorage.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return classicalStorage.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        return classicalStorage.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        classicalStorage.putAll(m);
    }

    @Override
    public void clear() {
        classicalStorage.clear();
    }

    @Override
    public Set<K> keySet() {
        return classicalStorage.keySet();
    }

    @Override
    public Collection<V> values() {
        return classicalStorage.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return classicalStorage.entrySet();
    }
}


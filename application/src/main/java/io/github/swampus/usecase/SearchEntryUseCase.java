package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.exception.QuantumInvalidRequestException;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumSearcher;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@AllArgsConstructor
public class SearchEntryUseCase {
    private final QuantumCollectionRepository repository;
    private final QuantumSearcher searcher;

    public QuantumResultModel execute(String collectionName, String key) {
        return repository.findByName(collectionName)
                .map(collection -> {
                    // 1) Normalize keys to a deterministic order.
                    // Preserve insertion order when available (LinkedHashSet/List),
                    // otherwise fall back to a stable sorted order.
                    List<String> effectiveKeys = normalizeOrder(collection.keys());

                    // 2) Validate: the searched key must be present in the provided keys.
                    if (key == null || key.isBlank() || !effectiveKeys.contains(key)) {
                        throw new QuantumInvalidRequestException(
                                "Key '" + key + "' is not present in the collection '" + collectionName + "'");
                    }

                    // 3) Delegate to the searcher with a well-defined ordering.
                    return searcher.search(key, effectiveKeys);
                })
                .orElseThrow(() -> new CollectionNotFoundException(collectionName));
    }

    private static List<String> normalizeOrder(Iterable<String> keys) {
        if (keys == null) {
            throw new QuantumInvalidRequestException("Collection has no keys");
        }

        // Fast-path: if incoming is already a List, copy to avoid accidental external mutation.
        if (keys instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(String.class::cast).toList());
        }

        // If it's a LinkedHashSet, insertion order is preserved — copy as-is.
        if (keys instanceof LinkedHashSet<?> linked) {
            return new ArrayList<>(linked.stream().map(String.class::cast).toList());
        }

        // Fallback: collect and sort to make the order deterministic.
        List<String> collected = new ArrayList<>();
        for (String k : keys) collected.add(k);
        if (collected.isEmpty()) {
            throw new QuantumInvalidRequestException("Collection has no keys");
        }
        collected.sort(String::compareTo); // stable, deterministic order
        return collected;
    }
}

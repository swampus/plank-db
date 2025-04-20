package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumCollection;
import io.github.swampus.model.QuantumResultModel;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumRangeSearcher;
import io.github.swampus.ports.QuantumSearcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RangeQueryUseCaseTest {

    private QuantumCollectionRepository repository;
    private QuantumRangeSearcher searcher;
    private RangeQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(QuantumCollectionRepository.class);
        searcher = mock(QuantumRangeSearcher.class);
        useCase = new RangeQueryUseCase(repository, searcher);
    }

    @Test
    void returnsResult_whenCollectionExists() {
        var collection = new QuantumCollection("demo");
        collection.put("k1", "v1");
        collection.put("k2", "v2");
        collection.put("k3", "v3");

        var expected = new QuantumResultModel();

        when(repository.findByName("demo")).thenReturn(Optional.of(collection));
        when(searcher.searchInRange(Set.of("k1", "k2", "k3"), "k1", "k3")).thenReturn(expected);

        var result = useCase.execute("demo", "k1", "k3");

        assertEquals(expected, result);
        verify(searcher).searchInRange(Set.of("k1", "k2", "k3"), "k1", "k3");
    }

    @Test
    void throwsCollectionNotFound_whenCollectionMissing() {
        when(repository.findByName("missing")).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class, () -> {
            useCase.execute("missing", "k1", "k3");
        });
    }
}


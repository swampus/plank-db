package io.github.swampus.usecase;

import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumRangeSearcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RangeQueryUseCaseTest {
    private QuantumCollectionRepository repository;
    private QuantumRangeSearcher searcher;
    private RangeQueryUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(QuantumCollectionRepository.class);
        searcher = mock(QuantumRangeSearcher.class);
        useCase = new RangeQueryUseCase(repository, searcher);
    }

    @Test
    void shouldReturnMatchFromRange() {
        QuantumCollection collection = new QuantumCollection("col");
        collection.put("a", "1");
        collection.put("b", "2");

        when(repository.findByName("col")).thenReturn(Optional.of(collection));
        when(searcher.searchInRange(Set.of("a", "b"), "a", "b"))
                .thenReturn(Optional.of("a"));

        Optional<String> result = useCase.execute("col", "a", "b");
        assertTrue(result.isPresent());
        assertEquals("a", result.get());
    }

    @Test
    void shouldReturnEmptyIfCollectionNotFound() {
        when(repository.findByName("missing")).thenReturn(Optional.empty());
        Optional<String> result = useCase.execute("missing", "a", "z");
        assertTrue(result.isEmpty());
    }
}

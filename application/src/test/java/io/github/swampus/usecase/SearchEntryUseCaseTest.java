package io.github.swampus.usecase;

import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumSearcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchEntryUseCaseTest {

    private QuantumCollectionRepository repository;
    private QuantumSearcher searcher;
    private SearchEntryUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(QuantumCollectionRepository.class);
        searcher = mock(QuantumSearcher.class);
        useCase = new SearchEntryUseCase(repository, searcher);
    }

    @Test
    void testExecute_successfulSearch() {
        String collectionName = "testCollection";
        String key = "testKey";
        String expectedValue = "someValue";

        QuantumCollection collection = mock(QuantumCollection.class);
        when(collection.keys()).thenReturn(Set.of("testKey", "anotherKey"));
        when(searcher.search(key, Set.of("testKey", "anotherKey"))).thenReturn(expectedValue);

        when(repository.findByName(collectionName)).thenReturn(Optional.of(collection));

        String result = useCase.execute(collectionName, key);
        assertEquals(expectedValue, result);
        verify(repository).findByName(collectionName);
        verify(searcher).search(key, Set.of("testKey", "anotherKey"));
    }

    @Test
    void testExecute_collectionNotFound() {
        String collectionName = "nonExistent";
        String key = "anyKey";

        when(repository.findByName(collectionName)).thenReturn(Optional.empty());

        assertThrows(CollectionNotFoundException.class, () ->
                useCase.execute(collectionName, key));
    }
}

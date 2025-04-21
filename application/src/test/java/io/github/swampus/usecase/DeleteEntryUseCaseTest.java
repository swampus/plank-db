package io.github.swampus.usecase;

import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeleteEntryUseCaseTest {

    private QuantumCollectionRepository repository;
    private DeleteEntryUseCase useCase;
    private QuantumCollection collection;

    @BeforeEach
    void setup() {
        repository = mock(QuantumCollectionRepository.class);
        useCase = new DeleteEntryUseCase(repository);
        collection = new QuantumCollection("test");
        collection.put("key1", "value1");
        when(repository.findByName("test")).thenReturn(Optional.of(collection));
    }

    @Test
    void shouldDeleteEntryFromCollection() {
        useCase.execute("test", "key1");
        assertNull(collection.get("key1"));
    }
}

package io.github.swampus.usecase;

import io.github.swampus.ports.QuantumCollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DeleteCollectionUseCaseTest {
    private QuantumCollectionRepository repository;
    private DeleteCollectionUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(QuantumCollectionRepository.class);
        useCase = new DeleteCollectionUseCase(repository);
    }

    @Test
    void shouldDeleteCollection() {
        useCase.execute("collection");
        verify(repository).delete("collection");
    }
}

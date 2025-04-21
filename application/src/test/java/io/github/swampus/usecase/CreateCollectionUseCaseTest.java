package io.github.swampus.usecase;

import io.github.swampus.ports.QuantumCollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CreateCollectionUseCaseTest {
    private QuantumCollectionRepository repository;
    private CreateCollectionUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(QuantumCollectionRepository.class);
        useCase = new CreateCollectionUseCase(repository);
    }

    @Test
    void shouldCreateCollection() {
        useCase.execute("newCollection");
        verify(repository).create("newCollection");
    }
}

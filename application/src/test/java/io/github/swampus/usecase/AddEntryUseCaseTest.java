package io.github.swampus.usecase;

import io.github.swampus.model.QuantumCollection;
import io.github.swampus.ports.QuantumCollectionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class AddEntryUseCaseTest {

    @Test
    void testAddEntryToExistingCollection() {

        QuantumCollectionRepository repository = mock(QuantumCollectionRepository.class);
        QuantumCollection collection = new QuantumCollection("test");
        when(repository.findByName("test")).thenReturn(Optional.of(collection));

        AddEntryUseCase useCase = new AddEntryUseCase(repository);


        useCase.execute("test", "key1", "value1");


        assert "value1".equals(collection.get("key1"));
    }
}
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



}

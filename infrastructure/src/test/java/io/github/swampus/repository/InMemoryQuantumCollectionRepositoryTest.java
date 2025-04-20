package io.github.swampus.repository;

import io.github.swampus.ports.QuantumCollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryQuantumCollectionRepositoryTest {
    private final QuantumCollectionRepository repo = new InMemoryQuantumCollectionRepository();

    @BeforeEach
    void setUp() {
        repo.create("demo");
        repo.findByName("demo").get().put("k1", "v1");
        repo.findByName("demo").get().put("k2", "v2");

    }

    @Test
    void testFindByName_returnsExpectedKeys() {
        var result = repo.findByName("demo");
        assertThat(result).isPresent();
        assertThat(result.get().keys()).contains("k1", "k2");
    }

    @Test
    void testFindByName_returnsEmptyForUnknown() {
        var result = repo.findByName("not_exists");
        assertThat(result).isEmpty();
    }
}
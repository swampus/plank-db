package io.github.swampus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.ports.QuantumCollectionRepository;
import io.github.swampus.ports.QuantumRangeSearcher;
import io.github.swampus.ports.QuantumSearcher;
import io.github.swampus.search.ibm.GroverIbmRangeSearcher;
import io.github.swampus.search.ibm.GroverIbmSearcher;
import io.github.swampus.search.local.GroverLocalRangeSearcher;
import io.github.swampus.search.local.GroverLocalSearcher;
import io.github.swampus.usecase.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public AddEntryUseCase addEntryUseCase(QuantumCollectionRepository repository) {
        return new AddEntryUseCase(repository);
    }

    @Bean
    public DeleteEntryUseCase deleteEntryUseCase(QuantumCollectionRepository repository) {
        return new DeleteEntryUseCase(repository);
    }

    @Bean
    public GetAllEntriesUseCase getAllEntriesUseCase(QuantumCollectionRepository repository) {
        return new GetAllEntriesUseCase(repository);
    }

    @Bean
    public CreateCollectionUseCase createCollectionUseCase(QuantumCollectionRepository repository) {
        return new CreateCollectionUseCase(repository);
    }

    @Bean
    public DeleteCollectionUseCase deleteCollectionUseCase(QuantumCollectionRepository repository) {
        return new DeleteCollectionUseCase(repository);
    }

    @Bean
    public SearchEntryUseCase searchEntryUseCase(
            QuantumCollectionRepository repository,
            QuantumSearcher searcher) {
        return new SearchEntryUseCase(repository, searcher);
    }

    @Bean
    public RangeQueryUseCase rangeQueryUseCase(
            QuantumCollectionRepository repository,
            QuantumRangeSearcher rangeSearcher) {
        return new RangeQueryUseCase(repository, rangeSearcher);
    }

    @Bean
    public QuantumSearcher quantumSearcher(
            ObjectMapper objectMapper,
            QuantumConfig config) {

        return switch (config.getQuantumExecutionMode()) {
            case LOCAL -> new GroverLocalSearcher(config, objectMapper);
            case IBM, IBM_REAL_PC -> new GroverIbmSearcher(config, objectMapper);
        };
    }

    @Bean
    public QuantumRangeSearcher quantumRangeSearcher(
            ObjectMapper objectMapper,
            QuantumConfig config) {

        return switch (config.getQuantumExecutionMode()) {
            case LOCAL -> new GroverLocalRangeSearcher(config, objectMapper);
            case IBM, IBM_REAL_PC -> new GroverIbmRangeSearcher(config, objectMapper);
        };
    }
}

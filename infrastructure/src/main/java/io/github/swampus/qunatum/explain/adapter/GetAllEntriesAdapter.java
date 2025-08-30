package io.github.swampus.qunatum.explain.adapter;

import io.github.swampus.port.out.CollectionReaderPort;
import io.github.swampus.usecase.GetAllEntriesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Adapter that reuses existing GetAllEntriesUseCase to satisfy the read port. */
@Component
@RequiredArgsConstructor
public class GetAllEntriesAdapter implements CollectionReaderPort {

    private final GetAllEntriesUseCase getAllEntriesUseCase;

    @Override
    public Map<String, String> getAllEntries(String collection) {
        // Delegates to the existing use case; keeps behavior the same.
        return getAllEntriesUseCase.execute(collection);
    }
}

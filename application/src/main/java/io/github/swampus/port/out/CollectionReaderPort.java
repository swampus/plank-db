package io.github.swampus.port.out;

import java.util.Map;

/** Read-only port to fetch all entries of a collection. */
public interface CollectionReaderPort {
    Map<String, String> getAllEntries(String collection);
}

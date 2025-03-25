package io.github.swampus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class GetAllEntriesResponse {
    private Map<String, String> entries;
}

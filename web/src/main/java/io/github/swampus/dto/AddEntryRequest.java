package io.github.swampus.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddEntryRequest {
    private String key;
    private String value;
}

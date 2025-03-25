package io.github.swampus.dto;

import lombok.Data;

@Data
public class AddEntryRequest {
    private String key;
    private String value;
}

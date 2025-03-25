package io.github.swampus.dto;

import lombok.Data;

@Data
public class RangeQueryRequest {
    private String collectionName;
    private String fromKey;
    private String toKey;
}

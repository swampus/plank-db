package io.github.swampus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RangeQueryRequest {
    @JsonProperty("fromKey")
    private String fromKey;

    @JsonProperty("toKey")
    private String toKey;
}

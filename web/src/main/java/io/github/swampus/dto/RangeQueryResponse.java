package io.github.swampus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class RangeQueryResponse {
    @JsonProperty("key")
    private String key;
}

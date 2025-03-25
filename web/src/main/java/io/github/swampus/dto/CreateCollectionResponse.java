package io.github.swampus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateCollectionResponse {
    private String collectionName;
    private String status;
}

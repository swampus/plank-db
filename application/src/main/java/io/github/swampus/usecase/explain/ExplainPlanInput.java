package io.github.swampus.usecase.explain;

import io.github.swampus.quntum.QueryMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExplainPlanInput {
    private QueryMode mode;     // KEY | RANGE
    private String key;         // when mode=KEY
    private String fromKey;     // when mode=RANGE
    private String toKey;       // when mode=RANGE

    private String backend;     // "local" | "ibm" (или null -> "local")
    private Strategy strategy;  // AUTO | FIXED (или null -> AUTO)
    private Integer iterations; // если strategy=FIXED
    private Integer shots;      // optional - default: 2048
    private Integer seed;       // optional
}

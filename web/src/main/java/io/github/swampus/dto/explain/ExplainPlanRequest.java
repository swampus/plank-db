package io.github.swampus.dto.explain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.swampus.quantum.QueryMode;
import io.github.swampus.usecase.explain.Strategy;          // AUTO | FIXED
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(name = "ExplainPlanRequest", description = "Explain plan input parameters")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExplainPlanRequest {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "KEY or RANGE")
    private QueryMode mode;

    @Schema(description = "Target key (required when mode=KEY)")
    private String key;

    @Schema(description = "Range lower bound (when mode=RANGE)")
    private String fromKey;

    @Schema(description = "Range upper bound (when mode=RANGE)")
    private String toKey;

    @Schema(description = "Backend: local (default) or ibm")
    private String backend;

    @Schema(description = "AUTO (default) or FIXED")
    private Strategy strategy;

    @Schema(description = "Used only when strategy=FIXED")
    private Integer iterations;

    private Integer shots;
    private Integer seed;

    //optional UX flags for simulator
    @Schema(description = "Render circuit/histogram PNGs (local only)")
    private Boolean render;

    @Schema(description = "Noise level 0.0..0.1 (local only)")
    private Double noise;

    @Schema(description = "Return top-K measurement states (local only)")
    private Integer topK;
}


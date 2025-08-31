package io.github.swampus.usecase.explain;

import io.github.swampus.quantum.QueryMode;
import lombok.Builder;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainPlanInput {
    // existing
    private QueryMode mode;
    private String key;
    private String fromKey;
    private String toKey;
    private String backend;     // "local" | "ibm"
    private Strategy strategy;  // AUTO | FIXED
    private Integer iterations;
    private Integer shots;
    private Integer seed;

    // NEW (optional)
    private Boolean render;     // default false
    private Double noise;       // default 0.0, clamp 0..0.1
    private Integer topK;       // optional

    // convenience (optional)
    public boolean isRender() { return Boolean.TRUE.equals(render); }
    public double noiseOrDefault() {
        double n = noise == null ? 0.0 : noise;
        return Math.max(0.0, Math.min(0.1, n));
    }
}


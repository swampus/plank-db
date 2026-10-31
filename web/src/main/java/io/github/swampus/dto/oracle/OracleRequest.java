package io.github.swampus.dto.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.swampus.oracle.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(description = "Virtual bit-vector query. AND/OR use exactly two children. OR requires manual iterations.")
public record OracleRequest(Integer width, OraclePredicate predicate, Integer iterations, Integer shots, Integer seed) {
    /** Strict parsing is local to this additive API; legacy Jackson coercion is unchanged. */
    public static OracleQuery parse(JsonNode body) {
        fields(body, Set.of("width", "predicate", "iterations", "shots", "seed"));
        return new OracleQuery(number(body, "width", true), predicate(body.get("predicate"), 1, new int[1]),
                number(body, "iterations", false), number(body, "shots", false), number(body, "seed", false));
    }

    private static OraclePredicate predicate(JsonNode body, int depth, int[] nodes) {
        if (++nodes[0] > 15 || depth > 6) throw OracleFailure.invalid("predicate exceeds node/depth budget");
        if (body == null || !body.isObject() || !body.path("type").isTextual()) throw OracleFailure.invalid("predicate type is required");
        OraclePredicate.Type type;
        try { type = OraclePredicate.Type.valueOf(body.get("type").textValue()); }
        catch (IllegalArgumentException error) { throw OracleFailure.invalid("unsupported predicate type"); }
        if (type == OraclePredicate.Type.AND || type == OraclePredicate.Type.OR) {
            fields(body, Set.of("type", "children"));
            JsonNode children = body.path("children");
            if (!children.isArray() || children.size() != 2) throw OracleFailure.invalid("AND/OR require exactly two children");
            return new OraclePredicate(type, null, null, List.of(predicate(children.get(0), depth + 1, nodes), predicate(children.get(1), depth + 1, nodes)));
        }
        fields(body, type == OraclePredicate.Type.EQUALS ? Set.of("type", "value") : Set.of("type", "mask", "value"));
        return new OraclePredicate(type, type == OraclePredicate.Type.EQUALS ? null : number(body, "mask", true), number(body, "value", true), null);
    }

    private static void fields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw OracleFailure.invalid("expected JSON object");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw OracleFailure.invalid("unknown field: " + field);
        });
    }

    private static Integer number(JsonNode body, String name, boolean required) {
        JsonNode value = body.get(name);
        if (value == null && !required) return null;
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw OracleFailure.invalid(name + " must be a 32-bit integer");
        return value.intValue();
    }
}

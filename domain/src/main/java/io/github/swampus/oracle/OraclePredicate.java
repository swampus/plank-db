package io.github.swampus.oracle;

import java.util.List;

/** Bounded bit-vector DSL. q[0] is the least significant bit. */
public record OraclePredicate(Type type, Integer mask, Integer value, List<OraclePredicate> children) {
    public enum Type { EQUALS, BITMASK_EQUALS, AND, OR }

    public OraclePredicate {
        children = children == null ? null : List.copyOf(children);
    }

    public int validate(int width, int depth) {
        if (type == null || depth > 6) throw OracleFailure.invalid("predicate type required; maximum depth is 6");
        if (type == Type.AND || type == Type.OR) {
            if (mask != null || value != null || children == null || children.size() != 2)
                throw OracleFailure.invalid("AND/OR require exactly two children and no mask/value");
            return 1 + children.stream().mapToInt(p -> p.validate(width, depth + 1)).sum();
        }
        int maximum = (1 << width) - 1;
        if (children != null || value == null || value < 0 || value > maximum)
            throw OracleFailure.invalid("leaf requires an in-width value and no children");
        if (type == Type.EQUALS && mask != null) throw OracleFailure.invalid("EQUALS does not accept mask");
        if (type == Type.BITMASK_EQUALS && (mask == null || mask < 0 || mask > maximum))
            throw OracleFailure.invalid("mask must fit the search width");
        return 1;
    }
}

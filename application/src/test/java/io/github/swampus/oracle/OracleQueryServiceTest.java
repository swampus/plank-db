package io.github.swampus.oracle;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OracleQueryServiceTest {
    private final OracleCompilerPort compiler = query -> { throw new AssertionError("compiler must not be called"); };
    private final OracleExecutionPort executor = query -> { throw new AssertionError("executor must not be called"); };
    private static OraclePredicate eq(int value) { return new OraclePredicate(OraclePredicate.Type.EQUALS, null, value, null); }

    @Test void disabledDoesNotInvokePython() {
        var service = new OracleQueryService(compiler, executor, false, 8, 16, 8192, 32);
        var error = assertThrows(OracleFailure.class, () -> service.search(new OracleQuery(4, eq(9), 3, 1024, 42)));
        assertEquals(OracleFailure.Kind.UNAVAILABLE, error.kind());
    }

    @Test void invalidQueriesNeverInvokePorts() {
        var service = new OracleQueryService(compiler, executor, true, 8, 16, 8192, 32);
        for (var query : List.of(new OracleQuery(9, eq(1), 3, 100, 42),
                new OracleQuery(4, eq(16), 3, 100, 42), new OracleQuery(4, eq(1), -1, 100, 42),
                new OracleQuery(4, eq(1), 33, 100, 42), new OracleQuery(4, eq(1), 1, 8193, 42),
                new OracleQuery(4, eq(1), 1, 0, 42), new OracleQuery(4, eq(1), 1, 100, -1),
                new OracleQuery(4, new OraclePredicate(OraclePredicate.Type.BITMASK_EQUALS, 16, 1, null), 1, 100, 42),
                new OracleQuery(4, new OraclePredicate(OraclePredicate.Type.AND, null, null, List.of(eq(1))), 1, 100, 42)))
            assertEquals(OracleFailure.Kind.INVALID, assertThrows(OracleFailure.class, () -> service.search(query)).kind());
    }

    @Test void totalQubitBudgetIsEnforced() {
        OraclePredicate p = eq(0);
        for (int i = 0; i < 5; i++) p = new OraclePredicate(OraclePredicate.Type.OR, null, null, List.of(p, eq(i)));
        var query = new OracleQuery(8, p, 1, 100, 42);
        var service = new OracleQueryService(compiler, executor, true, 8, 16, 8192, 32);
        assertThrows(OracleFailure.class, () -> service.search(query));
    }

    @Test void hardCeilingsCannotBeRaised() {
        assertThrows(IllegalArgumentException.class, () -> new OracleQueryService(compiler, executor, true, 20, 30, 100000, 1000));
    }
}

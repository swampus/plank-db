package io.github.swampus.oracle;

public interface OracleExecutionPort {
    OracleReport search(OracleQuery query);
}

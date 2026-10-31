package io.github.swampus.oracle;

public interface OracleCompilerPort {
    OracleReport compile(OracleQuery query);
}

package io.github.swampus.dto.oracle;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.github.swampus.oracle.OracleReport;

public record OracleResponse(@JsonUnwrapped OracleReport report, double endToEndMs) {}

package io.github.swampus.oracle;

public class OracleFailure extends RuntimeException {
    public enum Kind { INVALID, UNAVAILABLE, BUSY, TIMEOUT, BACKEND }
    private final Kind kind;

    public OracleFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
    public static OracleFailure invalid(String message) { return new OracleFailure(Kind.INVALID, message); }
}

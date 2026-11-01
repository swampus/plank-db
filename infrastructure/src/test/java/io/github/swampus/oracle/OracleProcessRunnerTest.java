package io.github.swampus.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class OracleProcessRunnerTest {
    @TempDir Path directory;

    private OracleProcessRunner runner(String mode, Duration timeout, Path pid) {
        return new OracleProcessRunner(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Fixture.class.getName(), mode, pid.toString()),
                Map.of(), timeout, 1, 1024);
    }

    @Test void timeoutKillsChildAndReleasesCapacity() throws Exception {
        Path pid = directory.resolve("pid");
        var runner = runner("sleep", Duration.ofSeconds(1), pid);
        var failure = assertThrows(OracleFailure.class, () -> runner.run(OracleProcessRunner.Action.search, "{}"));
        assertEquals(OracleFailure.Kind.TIMEOUT, failure.kind());
        long processId = Long.parseLong(Files.readString(pid));
        assertFalse(ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false));
        assertEquals(OracleFailure.Kind.TIMEOUT, assertThrows(OracleFailure.class,
                () -> runner.run(OracleProcessRunner.Action.search, "{}")).kind());
    }

    @Test void nonzeroExitAndStructuredInvalidInput() {
        assertEquals(OracleFailure.Kind.BACKEND, assertThrows(OracleFailure.class,
                () -> runner("fail", Duration.ofSeconds(5), directory.resolve("pid")).run(OracleProcessRunner.Action.search, "{}")).kind());
        OracleFailure invalid = assertThrows(OracleFailure.class,
                () -> runner("invalid", Duration.ofSeconds(5), directory.resolve("pid")).run(OracleProcessRunner.Action.search, "{}"));
        assertEquals(OracleFailure.Kind.INVALID, invalid.kind());
        assertEquals("manual iterations required", invalid.getMessage());
    }

    @Test void blockedStdinCannotDefeatDeadline() {
        var runner = runner("sleep", Duration.ofSeconds(1), directory.resolve("pid"));
        assertEquals(OracleFailure.Kind.TIMEOUT, assertThrows(OracleFailure.class,
                () -> runner.run(OracleProcessRunner.Action.search, " ".repeat(32768))).kind());
    }

    @Test void boundedStdoutAndStderr() {
        for (String mode : List.of("stdout", "stderr"))
            assertEquals(OracleFailure.Kind.BACKEND, assertThrows(OracleFailure.class,
                    () -> runner(mode, Duration.ofSeconds(5), directory.resolve("pid")).run(OracleProcessRunner.Action.search, "{}")).kind());
    }

    @Test void malformedAndIncompletePythonOutput() {
        var query = new OracleQuery(4, new OraclePredicate(OraclePredicate.Type.EQUALS, null, 9, null), 3, 1024, 42);
        for (String mode : List.of("malformed", "empty-report")) {
            var adapter = new AerOracleAdapter(runner(mode, Duration.ofSeconds(5), directory.resolve("pid")), new ObjectMapper());
            assertEquals(OracleFailure.Kind.BACKEND, assertThrows(OracleFailure.class, () -> adapter.search(query)).kind());
        }
    }

    @Test void concurrentRequestsAreBounded() throws Exception {
        Path pid = directory.resolve("pid");
        var runner = runner("sleep", Duration.ofSeconds(2), pid);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = pool.submit(() -> assertThrows(OracleFailure.class, () -> runner.run(OracleProcessRunner.Action.search, "{}")));
            long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (!Files.exists(pid) && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(Files.exists(pid));
            assertEquals(OracleFailure.Kind.BUSY, assertThrows(OracleFailure.class,
                    () -> runner.run(OracleProcessRunner.Action.search, "{}")).kind());
            running.get(5, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    /** Portable child process, independent of Python installation. */
    public static class Fixture {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
            switch (args[0]) {
                case "sleep" -> Thread.sleep(60000);
                case "fail" -> System.exit(4);
                case "invalid" -> { System.out.print("{\"error\":\"manual iterations required\"}"); System.exit(3); }
                case "stdout" -> System.out.print("x".repeat(100000));
                case "stderr" -> System.err.print("x".repeat(100000));
                case "malformed" -> System.out.print("not JSON");
                case "empty-report" -> System.out.print("{}");
                default -> throw new IllegalArgumentException();
            }
        }
    }
}

package io.github.swampus.oracle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Argument-array execution, bounded streams, per-process deadline, bounded concurrency. */
public class OracleProcessRunner {
    public enum Action { explain, search }
    private final List<String> command;
    private final Map<String, String> environment;
    private final Duration timeout;
    private final Semaphore slots;
    private final int outputLimit;

    public OracleProcessRunner(List<String> command, Map<String, String> environment, Duration timeout,
                               int concurrency, int outputLimit) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(120)) > 0
                || concurrency < 1 || concurrency > 4 || outputLimit < 1024 || outputLimit > 262144)
            throw new IllegalArgumentException("invalid oracle process resource limits");
        this.command = List.copyOf(command);
        this.environment = Map.copyOf(environment);
        this.timeout = timeout;
        this.slots = new Semaphore(concurrency);
        this.outputLimit = outputLimit;
    }

    public String run(Action action, String input) {
        if (input.getBytes(StandardCharsets.UTF_8).length > 32768) throw OracleFailure.invalid("oracle input exceeds 32 KiB");
        if (!slots.tryAcquire()) throw new OracleFailure(OracleFailure.Kind.BUSY, "oracle simulator concurrency limit reached");
        Process process = null;
        ExecutorService readers = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "oracle-stream-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            List<String> args = new ArrayList<>(command);
            args.add(action.name());
            ProcessBuilder builder = new ProcessBuilder(args);
            builder.environment().putAll(environment);
            process = builder.start();
            Process running = process;
            Future<String> stdout = readers.submit(() -> capture(running.getInputStream(), outputLimit));
            Future<String> stderr = readers.submit(() -> capture(running.getErrorStream(), Math.min(outputLimit, 32768)));
            Future<?> inputWriter = readers.submit(() -> {
                try (OutputStream stdin = running.getOutputStream()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            while (!process.waitFor(25, TimeUnit.MILLISECONDS)) {
                checkReader(stdout);
                checkReader(stderr);
                if (System.nanoTime() >= deadline) throw new OracleFailure(OracleFailure.Kind.TIMEOUT, "oracle Python execution timed out");
            }
            long remaining = Math.max(1, deadline - System.nanoTime());
            String output = stdout.get(remaining, TimeUnit.NANOSECONDS);
            stderr.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (process.exitValue() == 3) throw OracleFailure.invalid(invalidMessage(output));
            if (process.exitValue() != 0) throw new OracleFailure(OracleFailure.Kind.BACKEND, "oracle Python exited with code " + process.exitValue());
            inputWriter.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return output;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new OracleFailure(OracleFailure.Kind.BACKEND, "oracle execution interrupted");
        } catch (TimeoutException error) {
            throw new OracleFailure(OracleFailure.Kind.TIMEOUT, "oracle Python execution timed out");
        } catch (IOException | ExecutionException error) {
            throw new OracleFailure(OracleFailure.Kind.BACKEND, "oracle subprocess I/O failed or output limit exceeded");
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                if (process.isAlive()) process.destroyForcibly();
                try {
                    process.getInputStream().close();
                    process.getErrorStream().close();
                    process.getOutputStream().close();
                    process.waitFor(500, TimeUnit.MILLISECONDS);
                } catch (IOException ignored) {
                    // Stream may already have been closed by its reader.
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            readers.shutdownNow();
            slots.release();
        }
    }

    private static String invalidMessage(String output) {
        // JSON is parsed by the adapter for success; errors remain bounded and safe.
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(output).path("error").asText("invalid oracle request");
        } catch (IOException ignored) { return "invalid oracle request"; }
    }

    private static void checkReader(Future<String> reader) throws ExecutionException, InterruptedException {
        if (reader.isDone()) reader.get();
    }

    private static String capture(InputStream stream, int maximum) throws IOException {
        try (stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > maximum) throw new IOException("oracle output limit exceeded");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}

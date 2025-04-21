package io.github.swampus.qunatum;

import io.github.swampus.exception.AppException;
import io.github.swampus.exception.CollectionNotFoundException;
import io.github.swampus.exception.QuantumExternalServiceException;
import io.github.swampus.exception.QuantumInvalidInputException;
import io.github.swampus.ports.QuantumScriptExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuantumProcessRunner implements QuantumScriptExecutor {

    @Override
    public String run(String scriptPath, List<String> args) {
        try {
            String pythonExec = System.getenv("QUANTUM_PYTHON_EXEC");

            List<String> command = new ArrayList<>();
            command.add(pythonExec);
            command.add(scriptPath);
            command.addAll(args);


            var pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);

            var process = pb.start();

            var stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));
            var stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()))
                    .lines().collect(Collectors.joining("\n"));

            var exitCode = process.waitFor();
            return handleResult(exitCode, stdout, stderr);

        } catch (IOException | InterruptedException e) {
            throw new AppException("Failed to execute quantum script: " + e.getMessage(), e);
        }
    }

    private String handleResult(int exitCode, String stdout, String stderr) {
        return switch (exitCode) {
            case 0 -> stdout;
            case 2 -> throw new CollectionNotFoundException("Collection not found: " + stderr);
            case 3 -> throw new QuantumInvalidInputException("Invalid input: " + stderr);
            case 4 -> throw new QuantumExternalServiceException("Quantum backend error: " + stderr);
            default -> throw new AppException("Unexpected error (exit " + exitCode + "): " + stderr);
        };
    }
}

package io.github.swampus.config;

import io.github.swampus.quantum.PythonQuantumDryRunner;
import io.github.swampus.quantum.QuantumProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class QuantumInfraBeans {

    @Bean
    PythonQuantumDryRunner.QuantumEnvConfig quantumEnvConfig(
            @Value("${QUANTUM_PYTHON_EXEC:/opt/venv/bin/python}") String pythonExec
    ) {
        return () -> pythonExec;
    }

    @Bean
    public QuantumProcessRunner quantumProcessRunner() {
        return new QuantumProcessRunner();
    }
}

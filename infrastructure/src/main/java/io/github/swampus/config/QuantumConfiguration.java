package io.github.swampus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.swampus.ports.QuantumSearcher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuantumConfig.class)
public class QuantumConfiguration {

    @Bean
    public QuantumRuntimeConfig quantumRuntimeConfig() {
        return new QuantumRuntimeConfig();
    }

    @Bean
    public QuantumSearcherFactory quantumSearcherFactory(
            QuantumRuntimeConfig runtimeConfig,
            QuantumConfig staticConfig,
            ObjectMapper objectMapper
    ) {
        return new QuantumSearcherFactory(runtimeConfig, staticConfig, objectMapper);
    }

    @Bean
    public QuantumSearcher quantumSearcher(QuantumSearcherFactory factory) {
        return factory.create();
    }
}

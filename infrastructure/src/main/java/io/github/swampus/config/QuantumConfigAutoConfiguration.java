package io.github.swampus.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuantumConfig.class)
public class QuantumConfigAutoConfiguration {
}
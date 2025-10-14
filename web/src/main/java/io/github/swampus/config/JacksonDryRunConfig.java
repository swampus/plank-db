package io.github.swampus.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.swampus.dto.DryRunResultMixin;
import io.github.swampus.quantum.DryRunResult;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers MixIn and global JSON settings for web layer.
 */
@Configuration
public class JacksonDryRunConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dryRunMixins() {
        return builder -> {
            builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            builder.mixIn(DryRunResult.class, DryRunResultMixin.class);
            builder.modulesToInstall(new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}


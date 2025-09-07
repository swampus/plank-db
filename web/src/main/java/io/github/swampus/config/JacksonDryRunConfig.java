package io.github.swampus.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
    Jackson2ObjectMapperBuilderCustomizer dryRunMixins() {
        return builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE) // keep snake_case everywhere
                .mixIn(DryRunResult.class, DryRunResultMixin.class)
                .modules(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()) // support OffsetDateTime
                .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }


}


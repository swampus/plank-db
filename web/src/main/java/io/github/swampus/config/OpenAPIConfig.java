package io.github.swampus.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI quantumOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PlankDB: Quantum Key-Value Store")
                        .description("""
                                PlankDB is an experimental key-value database that integrates quantum search (Grover's algorithm) 
                                for probabilistic key discovery. It supports both IBM Quantum and local simulation backends. 
                                Designed for educational and research purposes.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PlankDB Contributors")
                                .url("https://github.com/swampus/plank-db")
                                .email("dmitrijs.gavrilovs.swampus@gmail.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                )
                .externalDocs(new ExternalDocumentation()
                        .description("Read more about Grover's Algorithm on Qiskit Docs")
                        .url("https://qiskit.org/textbook/ch-algorithms/grover.html"));
    }
}


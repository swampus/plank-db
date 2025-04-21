package io.github.swampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.swampus")
public class PlankDbApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlankDbApplication.class, args);
    }
}

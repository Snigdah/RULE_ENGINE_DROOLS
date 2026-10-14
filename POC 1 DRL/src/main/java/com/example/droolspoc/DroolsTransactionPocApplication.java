package com.example.droolspoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.example.droolspoc.model")
@EnableJpaRepositories(basePackages = "com.example.droolspoc.repository")
public class DroolsTransactionPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(DroolsTransactionPocApplication.class, args);
    }
}

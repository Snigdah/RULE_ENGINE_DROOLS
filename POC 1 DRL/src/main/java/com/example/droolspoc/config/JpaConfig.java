package com.example.droolspoc.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Kept off the application class so @WebMvcTest slices don't try to build JPA repositories. */
@Configuration
@EntityScan(basePackages = "com.example.droolspoc.model")
@EnableJpaRepositories(basePackages = "com.example.droolspoc.repository")
public class JpaConfig {
}

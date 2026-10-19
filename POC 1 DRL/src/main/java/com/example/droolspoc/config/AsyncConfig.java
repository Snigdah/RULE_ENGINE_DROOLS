package com.example.droolspoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor used to fan out the independent DB lookups in each buildContext(...) onto
 * virtual threads so they run in parallel. One virtual thread per task — cheap to create.
 */
@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService lookupExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

package com.example.droolspoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ConcurrencyConfig {

    /**
     * Executor that spawns one Java 21 virtual thread per task. Virtual threads
     * are extremely cheap, so a thread-per-task model is ideal for fan-out
     * blocking I/O such as running the two context DB lookups in parallel.
     * Bean is closed on shutdown.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

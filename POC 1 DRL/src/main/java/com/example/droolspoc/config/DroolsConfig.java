package com.example.droolspoc.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Compiles the DRL files under classpath:/rules/ into a KieContainer once,
 * at application startup. The container is thread-safe and shared; each
 * request creates its own short-lived KieSession from it (see the service).
 */
@Configuration
public class DroolsConfig {

    @Bean
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();

        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        // Public KieResources API instead of the internal drools-core class.
        kieFileSystem.write(
                kieServices.getResources()
                        .newClassPathResource("rules/transaction-rules.drl")
        );

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new IllegalStateException(
                    "DRL compilation failed: " + results.getMessages());
        }

        return kieServices.newKieContainer(
                kieServices.getRepository().getDefaultReleaseId());
    }
}

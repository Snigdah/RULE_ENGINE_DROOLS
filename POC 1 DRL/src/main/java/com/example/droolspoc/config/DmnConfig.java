package com.example.droolspoc.config;

import org.kie.api.io.ResourceType;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.internal.io.ResourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds one DMNRuntime at startup holding EVERY .dmn model found under
 * classpath:/dmn/. Drop a new .dmn file there and it is loaded automatically -
 * no config change needed. Each model is later picked by (namespace, name).
 */
@Configuration
public class DmnConfig {

    @Bean
    public DMNRuntime dmnRuntime() {
        List<org.kie.api.io.Resource> models = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            for (org.springframework.core.io.Resource r : resolver.getResources("classpath*:dmn/**/*.dmn")) {
                models.add(ResourceFactory.newClassPathResource("dmn/" + r.getFilename())
                        .setResourceType(ResourceType.DMN));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan DMN resources", e);
        }

        return DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromResources(models)
                .getOrElseThrow(e -> new IllegalStateException("Failed to load DMN models", e));
    }
}

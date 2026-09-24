package com.example.droolspoc.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs once, after the application context is ready, and asks EVERY registered
 * {@link GlobalReferenceLoader} to load its dataset into {@link GlobalContext}.
 *
 * <p>Spring injects the full list of loader beans, so a newly added loader is
 * picked up automatically — this class never changes when you add a dataset.</p>
 */
@Component
public class GlobalContextLoader implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalContextLoader.class);

    private final List<GlobalReferenceLoader> loaders;
    private final GlobalContext globalContext;

    public GlobalContextLoader(List<GlobalReferenceLoader> loaders, GlobalContext globalContext) {
        this.loaders = loaders;
        this.globalContext = globalContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Building global context from {} loader(s)...", loaders.size());
        for (GlobalReferenceLoader loader : loaders) {
            loader.load(globalContext);
        }
        LOGGER.info("Global context ready: {} dataset(s) loaded.", globalContext.size());
    }
}

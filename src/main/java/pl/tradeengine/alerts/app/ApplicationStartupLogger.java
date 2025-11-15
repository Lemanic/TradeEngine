package pl.tradeengine.alerts.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupLogger.class);

    @Autowired
    private Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String version = environment.getProperty("spring.application.version");
        String profiles = String.join(", ", environment.getActiveProfiles());
        log.info("App version: {}", version);
        log.info("Active profiles: {}", profiles);
    }
}

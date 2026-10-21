package pl.tradeengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.tradeengine.adapter.inbound.PortfolioSnapshotScheduler;
import pl.tradeengine.application.service.portfolio.PortfolioSnapshotService;

/**
 * Scheduling is enabled only with {@code portfolio.enabled: true} — the application had no
 * {@code @EnableScheduling} before the portfolio module, and tests keep the flag off.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "portfolio", name = "enabled", havingValue = "true")
public class PortfolioSchedulerConfig {

    @Bean
    public PortfolioSnapshotScheduler portfolioSnapshotScheduler(PortfolioSnapshotService service) {
        return new PortfolioSnapshotScheduler(service);
    }
}

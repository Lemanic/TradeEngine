package pl.tradeengine.adapter.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import pl.tradeengine.application.service.portfolio.PortfolioSnapshotService;

/**
 * Drives the hourly snapshot. Registered by {@code PortfolioSchedulerConfig}, so it exists only
 * when {@code portfolio.enabled} is true.
 */
public class PortfolioSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotScheduler.class);

    private final PortfolioSnapshotService service;

    public PortfolioSnapshotScheduler(PortfolioSnapshotService service) {
        this.service = service;
    }

    @Scheduled(cron = "${portfolio.cron:0 0 * * * *}", zone = "UTC")
    public void takeHourlySnapshot() {
        try {
            service.takeSnapshot();
        } catch (RuntimeException e) {
            // Never let a failure kill the schedule — the next hour tries again.
            log.error("Hourly portfolio snapshot failed: {}", e.toString(), e);
        }
    }
}

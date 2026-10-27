package pl.tradeengine.adapter.inbound;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import pl.tradeengine.FakeNotificationPublisher;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidException;
import pl.tradeengine.application.service.fill.FakeTradeFillSource;
import pl.tradeengine.application.service.fill.FillAlertService;
import pl.tradeengine.application.service.fill.InMemoryTradeFillRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Dwie rzeczy: bez adresu nic nie jest planowane, a blad przebiegu nie zabija harmonogramu. */
class FillAlertSchedulerTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(45);

    private final FakeTradeFillSource source = new FakeTradeFillSource();

    private FillAlertScheduler scheduler(boolean active) {
        FillAlertService service = new FillAlertService(source, new InMemoryTradeFillRepository(),
                new FakeNotificationPublisher(),
                Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC),
                10, Duration.ofHours(24), Duration.ofDays(30));
        return new FillAlertScheduler(service, POLL_INTERVAL, active);
    }

    @Test
    void schedulesOnePollingTaskWhenThereIsAnAddress() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler(true).configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).hasSize(1);
        assertThat(registrar.getFixedDelayTaskList().getFirst().getIntervalDuration())
                .isEqualTo(POLL_INTERVAL);
    }

    @Test
    void schedulesNothingWithoutAnAddress() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        // Brak adresu to ostrzezenie w logu, nie wywrocony start — tak jak reszta modulu.
        scheduler(false).configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).isEmpty();
    }

    @Test
    void aFailedRunDoesNotKillTheSchedule() {
        source.fails(new HyperliquidException("Hyperliquid is unreachable for userFillsByTime"));

        assertThatCode(() -> scheduler(true).poll()).doesNotThrowAnyException();
    }
}

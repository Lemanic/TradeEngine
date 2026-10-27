package pl.tradeengine.adapter.inbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import pl.tradeengine.application.service.fill.FillAlertService;

import java.time.Duration;

/**
 * Napędza odpytywanie o zrealizowane zlecenia. Rejestrowany przez {@code FillAlertConfig}, więc
 * istnieje tylko przy {@code portfolio.hyperliquid.fill-alerts.enabled: true}.
 *
 * <p>Zadanie jest dopisywane programowo, a nie adnotacją {@code @Scheduled}: ta przyjmuje w
 * {@code fixedDelayString} wyłącznie liczbę milisekund albo zapis ISO-8601, więc czytelne
 * {@code 45s} z YAML-a wywróciłoby start aplikacji. {@link ScheduledTaskRegistrar} bierze
 * {@link Duration} wprost.
 *
 * <p>{@code fixedDelay}, nie {@code fixedRate}: liczy się odstęp od <em>zakończenia</em>
 * poprzedniego przebiegu, więc wolna odpowiedź giełdy nie buduje kolejki nakładających się
 * odpytań.
 */
public class FillAlertScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(FillAlertScheduler.class);

    private final FillAlertService service;
    private final Duration pollInterval;
    private final boolean active;

    public FillAlertScheduler(FillAlertService service, Duration pollInterval, boolean active) {
        this.service = service;
        this.pollInterval = pollInterval;
        this.active = active;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!active) {
            log.warn("Alerty o filach sa wlaczone, ale zaden adres Hyperliquid nie jest"
                    + " skonfigurowany — nic nie bedzie odpytywane");
            return;
        }
        registrar.addFixedDelayTask(this::poll, pollInterval);
        log.info("Alerty o filach na Hyperliquid: odpytywanie co {}", pollInterval);
    }

    void poll() {
        try {
            service.pollAndNotify();
        } catch (RuntimeException e) {
            // Nigdy nie pozwol bledowi zabic harmonogramu — nastepny przebieg probuje znowu.
            log.error("Przebieg alertow o filach nie udal sie: {}", e.toString(), e);
        }
    }
}

package pl.tradeengine.application.service;

import pl.tradeengine.FakeAlertPublisher;
import pl.tradeengine.RecordingBiasRepositoryFake;
import pl.tradeengine.RecordingScenario;
import pl.tradeengine.StatefulFvgRepositoryFake;
import pl.tradeengine.StatefulSwingPointRepositoryFake;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.config.ScenarioRegistry;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.ScenarioEngine;

import java.util.List;

/**
 * Sklada prawdziwy lancuch produkcyjny bez Springa:
 * {@code WebhookProcessingService -> ScenarioEngine -> ScenarioRegistry -> RecordingScenario}
 * oraz {@code AlertDispatchService -> FakeAlertPublisher}.
 *
 * <p>Po co, skoro istniejacy WebhookProcessingServiceHandlePriceUpdateTest mockuje ScenarioEngine:
 * mock zaslepiony przez {@code any()} nie mowi nic o tym, <b>ktore</b> zdarzenie serwis wyemitowal,
 * a to jest cala tresc jego kontraktu. Tutaj zdarzenia da sie odczytac z {@link #scenario}, a to,
 * czy alert faktycznie doszedl do publishera — z {@link #publisher}.
 *
 * <p>Scenariusz jest wlaczony na wszystkich timeframe'ach, zeby routing w ScenarioEngine nigdy nie
 * byl powodem, dla ktorego zdarzenie nie doszlo (routing ma swoje testy w ScenarioEngineTest).
 */
public final class WebhookChainHarness {

    public static final String SCENARIO_NAME = "TEST_RECORDING_SCENARIO";

    public final RecordingScenario scenario;
    public final FakeAlertPublisher publisher;
    public final StatefulFvgRepositoryFake fvgRepo = new StatefulFvgRepositoryFake();
    public final InMemoryDivergenceRepository divergenceRepo = new InMemoryDivergenceRepository();
    public final RecordingBiasRepositoryFake biasRepo = new RecordingBiasRepositoryFake();
    public final StatefulSwingPointRepositoryFake swingRepo = new StatefulSwingPointRepositoryFake();
    public final WebhookProcessingService service;

    private WebhookChainHarness(List<AlertToSend> alertsFromScenario) {
        this.scenario = new RecordingScenario(SCENARIO_NAME, 99L, alertsFromScenario);
        this.publisher = new FakeAlertPublisher();

        ScenarioRegistry registry = new ScenarioRegistry(
                List.of(scenario),
                TestFixtures.strategies(
                        TestFixtures.strategy(SCENARIO_NAME, true, Timeframe.values())));

        this.service = new WebhookProcessingService(
                new ScenarioEngine(registry),
                new AlertDispatchService(publisher),
                fvgRepo, divergenceRepo, biasRepo, swingRepo);
    }

    /** Lancuch, w ktorym scenariusz nic nie zwraca — do asercji na samych zdarzeniach. */
    public static WebhookChainHarness silent() {
        return new WebhookChainHarness(List.of());
    }

    /** Lancuch, w ktorym scenariusz zwraca podane alerty — do asercji na dojsciu do publishera. */
    public static WebhookChainHarness returningAlerts(AlertToSend... alerts) {
        return new WebhookChainHarness(List.of(alerts));
    }

    /** Czysci zapisane zdarzenia i alerty — @TestConfiguration tworzy serwis raz na kontekst. */
    public void clear() {
        scenario.clear();
        publisher.clear();
        biasRepo.clear();
        swingRepo.clear();
    }
}

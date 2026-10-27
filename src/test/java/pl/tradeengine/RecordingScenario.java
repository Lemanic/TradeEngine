package pl.tradeengine;

import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.scenario.Scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Scenariusz, ktory nie ma zadnej logiki: zapisuje kazde zdarzenie, jakie zobaczyl, i zwraca
 * ustalona z gory liste alertow.
 *
 * <p>Po to, zeby dalo sie sprawdzic, <b>ktore</b> zdarzenie wyszlo z WebhookProcessingService.
 * Istniejacy WebhookProcessingServiceHandlePriceUpdateTest zaslepia ScenarioEngine przez
 * {@code when(onEvent(any()))}, wiec nic tam nie pilnuje, czy poszedl FvgTouchedEvent czy
 * FvgFilledEvent — a to jest cala tresc kontraktu miedzy serwisem a scenariuszami.
 *
 * <p>Zlozony z ScenarioRegistry, ScenarioEngine, AlertDispatchService i {@link FakeAlertPublisher}
 * daje prawdziwy lancuch produkcyjny bez Springa.
 */
public class RecordingScenario implements Scenario {

    private final String name;
    private final Long id;
    private final List<AlertToSend> alertsToReturn;
    private final List<DomainEvent> seen = new ArrayList<>();

    public RecordingScenario(String name, Long id, List<AlertToSend> alertsToReturn) {
        this.name = name;
        this.id = id;
        this.alertsToReturn = alertsToReturn;
    }

    /** Scenariusz, ktory nic nie zwraca — do testow samego przeplywu zdarzen. */
    public static RecordingScenario silent(String name) {
        return new RecordingScenario(name, 1L, List.of());
    }

    /** Scenariusz, ktory na kazde zdarzenie zwraca podane alerty. */
    public static RecordingScenario returning(String name, AlertToSend... alerts) {
        return new RecordingScenario(name, 1L, List.of(alerts));
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        seen.add(event);
        return alertsToReturn;
    }

    @Override
    public Long id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /** Zdarzenia w kolejnosci, w jakiej doszly. */
    public List<DomainEvent> seen() {
        return List.copyOf(seen);
    }

    /** Pierwsze zdarzenie danego typu — wygodne, gdy jedno wywolanie serwisu emituje kilka. */
    public <T extends DomainEvent> T firstOfType(Class<T> type) {
        return seen.stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("Brak zdarzenia typu " + type.getSimpleName()
                        + "; widziane: " + seen.stream().map(e -> e.getClass().getSimpleName()).toList()));
    }

    public <T extends DomainEvent> List<T> allOfType(Class<T> type) {
        return seen.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public void clear() {
        seen.clear();
    }
}

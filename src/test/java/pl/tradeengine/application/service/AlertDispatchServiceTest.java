package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.FakeAlertPublisher;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.port.AlertPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jedyna brama miedzy scenariuszami a publisherem. 26 linii, ale kazdy alert w systemie przez nia
 * przechodzi, wiec cicha zmiana tutaj wycisza cala aplikacje.
 */
class AlertDispatchServiceTest {

    private FakeAlertPublisher publisher;
    private AlertDispatchService service;

    @BeforeEach
    void setUp() {
        publisher = new FakeAlertPublisher();
        service = new AlertDispatchService(publisher);
    }

    @Test
    void shouldNotTouchThePublisher_whenAlertListIsNull() {
        service.dispatch(null);

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void shouldNotTouchThePublisher_whenAlertListIsEmpty() {
        // Wazne, bo ScenarioEngine zwraca pusta liste przy kazdym zdarzeniu, ktorego zaden
        // scenariusz nie obsluguje — czyli przy wiekszosci swiec.
        service.dispatch(List.of());

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void shouldPassTheWholeListInOneCall_whenAlertsArePresent() {
        AlertToSend first = TestFixtures.alert("FIRST");
        AlertToSend second = TestFixtures.alert("SECOND");

        service.dispatch(List.of(first, second));

        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().get(0)).containsExactly(first, second);
    }

    @Test
    void shouldPropagatePublisherFailure_becauseThereIsNoTryCatchHere() {
        // Utrwalone swiadomie: awaria Telegrama wychodzi az do wolajacego, czyli do
        // WebhookProcessingService, a tam dalej do catch-all w kontrolerze (400 dla TradingView).
        AlertPublisher throwing = alerts -> {
            throw new IllegalStateException("telegram down");
        };

        assertThatThrownBy(() -> new AlertDispatchService(throwing).dispatch(List.of(TestFixtures.alert("X"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("telegram down");
    }
}

package pl.tradeengine;

import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.port.AlertPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Zapisuje to, co dostal, zamiast publikowac. Istnieje, bo w tej suicie nie weryfikujemy mockow
 * (nie ma ani jednego Mockito.verify) — a AlertDispatchService da sie sprawdzic tylko po tym,
 * czy i z czym zawolal publishera.
 *
 * <p>Trzyma cale listy, nie splaszczone alerty, bo kontraktem AlertDispatchService jest jedno
 * wywolanie publish() na jedno dispatch() — splaszczenie zgubiloby te informacje.
 */
public class FakeAlertPublisher implements AlertPublisher {

    private final List<List<AlertToSend>> published = new ArrayList<>();

    @Override
    public void publish(List<AlertToSend> alerts) {
        published.add(alerts);
    }

    /** Kolejne wywolania publish(), kazde jako osobna lista. */
    public List<List<AlertToSend>> published() {
        return List.copyOf(published);
    }

    /** Wszystkie alerty ze wszystkich wywolan, splaszczone. */
    public List<AlertToSend> allAlerts() {
        return published.stream().flatMap(List::stream).toList();
    }

    public void clear() {
        published.clear();
    }
}

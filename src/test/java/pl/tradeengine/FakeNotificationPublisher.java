package pl.tradeengine;

import pl.tradeengine.domain.port.NotificationPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Zapisuje wyslane wiadomosci zamiast je wysylac. Istnieje z tego samego powodu co
 * {@link FakeAlertPublisher}: w tej suicie nie ma ani jednego Mockito.verify, a to, co dostal
 * publisher, jest jedynym obserwowalnym skutkiem przebiegu alertow.
 */
public class FakeNotificationPublisher implements NotificationPublisher {

    private final List<String> sent = new ArrayList<>();

    @Override
    public void send(String text) {
        sent.add(text);
    }

    public List<String> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }
}

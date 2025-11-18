package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.AlertToSend;

public interface AlertPublisher {
    void publish(AlertToSend alert);
}

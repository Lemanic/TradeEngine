package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

public interface AlertPublisher {
    void publish(List<AlertToSend> alerts);
}

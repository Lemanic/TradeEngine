package pl.tradeengine.domain.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.tradeengine.domain.model.AlertToSend;

@Service
public class LoggingAlertPublisher implements AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertPublisher.class);

    @Override
    public void publish(AlertToSend alert) {
        log.info("ALERT: {}", alert);
    }
}

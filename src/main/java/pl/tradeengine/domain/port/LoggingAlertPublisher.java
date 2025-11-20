package pl.tradeengine.domain.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

@Service
@Profile("dev")
public class LoggingAlertPublisher implements AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertPublisher.class);

    @Override
    public void publish(List<AlertToSend> alerts) {
        for (AlertToSend alert : alerts) {
            log.info("ALERT: {}", alert.toString());
        }
    }
}

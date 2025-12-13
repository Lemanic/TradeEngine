package pl.tradeengine.application.service;

import org.springframework.stereotype.Service;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.port.AlertPublisher;

import java.util.List;

@Service
public class AlertDispatchService {

    private final AlertPublisher alertPublisher;

    public AlertDispatchService(AlertPublisher alertPublisher) {
        this.alertPublisher = alertPublisher;
    }

    public void dispatch(List<AlertToSend> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return;
        }

        alertPublisher.publish(alerts);
    }
}


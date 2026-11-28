package pl.tradeengine.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.port.AlertMuteRepository;
import pl.tradeengine.domain.port.AlertPublisher;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AlertDispatchService {

    private final AlertPublisher alertPublisher;
    private final AlertMuteRepository muteRepository;

    public AlertDispatchService(AlertPublisher alertPublisher, AlertMuteRepository muteRepository) {
        this.alertPublisher = alertPublisher;
        this.muteRepository = muteRepository;
    }

    public void dispatch(List<AlertToSend> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return;
        }

        List<AlertToSend> audible = withoutMuted(alerts);
        if (audible.isEmpty()) {
            return;
        }

        alertPublisher.publish(audible);
    }

    /**
     * The rules are read per dispatch rather than cached: alerts are rare, so one query each costs
     * nothing, and there is no cache to invalidate when the dashboard changes a rule.
     */
    private List<AlertToSend> withoutMuted(List<AlertToSend> alerts) {
        List<AlertMute> mutes;
        try {
            mutes = muteRepository.findAll();
        } catch (RuntimeException e) {
            // A redundant alert beats a missed one: unreadable rules mute nothing.
            log.error("Could not read alert mutes, sending {} alert(s) unfiltered", alerts.size(), e);
            return alerts;
        }
        if (mutes.isEmpty()) {
            return alerts;
        }

        return alerts.stream()
                .filter(alert -> {
                    Optional<AlertMute> mute = mutes.stream().filter(m -> m.covers(alert)).findFirst();
                    mute.ifPresent(m -> log.info("🔇 MUTED {} {} {} (rule: {}): {}",
                            alert.getScenarioName(), alert.getSymbol().code(), alert.getTimeframe(),
                            m.timeframe() == null ? "all timeframes" : m.timeframe(),
                            alert.getDescription()));
                    return mute.isEmpty();
                })
                .toList();
    }
}

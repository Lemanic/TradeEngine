package pl.tradeengine.domain.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
// Profil `test` obok `dev` z tego samego powodu co w LoggingAlertPublisher: poza `prod`
// NotificationPublisher nie ma innej implementacji, a kazdy @SpringBootTest z
// @ActiveProfiles("test") nie wstalby na braku beana.
@Profile({"dev", "test"})
public class LoggingNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationPublisher.class);

    @Override
    public void send(String text) {
        log.info("NOTIFICATION: {}", text);
    }
}

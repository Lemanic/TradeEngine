package pl.tradeengine.domain.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pl.tradeengine.domain.model.AlertToSend;

import java.util.List;

@Service
// Profil `test` jest tu obok `dev`, bo AlertPublisher nie ma zadnej innej implementacji poza
// profilem `prod` (Telegram). Bez tego kazdy @SpringBootTest z @ActiveProfiles("test") nie wstaje
// na braku beana AlertPublisher — a wtedy contextLoads musi chodzic pod `dev` i zalezy od
// zmiennych srodowiskowych oraz od git-ignorowanego config/application-dev.yml.
@Profile({"dev", "test"})
public class LoggingAlertPublisher implements AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertPublisher.class);

    @Override
    public void publish(List<AlertToSend> alerts) {
        for (AlertToSend alert : alerts) {
            log.info("ALERT: {}", alert.toString());
        }
    }
}

package pl.tradeengine.adapter.outbound.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.tradeengine.domain.port.NotificationPublisher;

import java.time.Duration;
import java.util.Map;

/**
 * Wysyła wiadomość przez Bot API Telegrama, zwykłym POST-em na {@code sendMessage}.
 *
 * <p><b>Dlaczego nie przez {@link TelegramAlertPublisher}</b>, który ma już bota: tamten rejestruje
 * {@code TelegramLongPollingBot}, a drugi bot na tym samym tokenie to dwa równoległe
 * {@code getUpdates} i konflikt HTTP 409 od Telegrama. Do wysyłania wiadomości long polling nie
 * jest do niczego potrzebny — ta aplikacja i tak nie odbiera komend.
 *
 * <p>Bez {@code parse_mode}, czyli treść idzie dosłownie: żaden znak w tickerze ani w liczbie nie
 * zostanie wzięty za składnię Markdownu.
 */
@Service
@Profile("prod")
public class TelegramNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationPublisher.class);

    private static final String API_BASE_URL = "https://api.telegram.org";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final String botToken;
    private final String chatId;
    private final RestClient restClient;

    public TelegramNotificationPublisher(@Value("${app.telegram.bot-token}") String botToken,
                                         @Value("${app.telegram.chat-id}") String chatId) {
        this(API_BASE_URL, botToken, chatId, RestClient.builder()
                .requestFactory(requestFactory())
                .build());
    }

    TelegramNotificationPublisher(String baseUrl, String botToken, String chatId,
                                  RestClient restClient) {
        this.baseUrl = baseUrl;
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = restClient;
    }

    @Override
    public void send(String text) {
        try {
            restClient.post()
                    .uri(baseUrl + "/bot" + botToken + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId,
                            "text", text,
                            "disable_web_page_preview", true))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Wyslano powiadomienie na Telegram: {}", firstLine(text));
        } catch (RestClientException e) {
            // Nieudana wysylka nie moze wywrocic przebiegu, ktory ja zlecil — kontrakt portu.
            log.error("Nie udalo sie wyslac powiadomienia na Telegram: {}", e.getMessage());
        }
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}

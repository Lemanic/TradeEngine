package pl.tradeengine.adapter.outbound.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Ten publisher istnieje m.in. dlatego, ze da sie go sprawdzic — wariant na long pollingu nie ma
 * ani jednego testu. Pilnujemy dwoch rzeczy: adresu z tokenem (dwukropek w tokenie nie moze
 * zostac zakodowany) i tego, ze nieudana wysylka nie leci wyjatkiem w gore.
 */
class TelegramNotificationPublisherTest {

    private static final String BASE_URL = "https://api.telegram.test";
    private static final String TOKEN = "123456789:AAEhBOweik6ad9r_Qw";
    private static final String CHAT_ID = "-1001234567890";

    private MockRestServiceServer server;
    private TelegramNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        publisher = new TelegramNotificationPublisher(BASE_URL, TOKEN, CHAT_ID, builder.build());
    }

    /**
     * Ten bean jest @Profile("prod"), wiec zaden @SpringBootTest w profilu test go nie tworzy —
     * i wlasnie dlatego brakujacy @Autowired przy DWOCH konstruktorach wyszedl dopiero na
     * produkcji, jako "No default constructor found" i wywrocony start calej aplikacji.
     * Ten test odtwarza samo rozstrzyganie konstruktora przez Springa: bez kontekstu Boota,
     * bez bazy, w kilka milisekund.
     */
    @Test
    void springResolvesTheInjectedConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "telegram", Map.<String, Object>of(
                            "app.telegram.bot-token", TOKEN,
                            "app.telegram.chat-id", CHAT_ID)));
            context.register(TelegramNotificationPublisher.class);
            context.refresh();

            assertThat(context.getBean(TelegramNotificationPublisher.class)).isNotNull();
        }
    }

    @Test
    void sendsTheTextToTheConfiguredChat() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(CHAT_ID))
                .andExpect(jsonPath("$.text").value("🟢 HYPERLIQUID · Open Long\nBTC"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        publisher.send("🟢 HYPERLIQUID · Open Long\nBTC");

        server.verify();
    }

    @Test
    void doesNotSendParseModeSoTheTextIsTakenLiterally() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/sendMessage"))
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        publisher.send("BTC_PERP *1.5* _x_");

        server.verify();
    }

    @Test
    void aFailedSendDoesNotReachTheCaller() {
        server.expect(requestTo(BASE_URL + "/bot" + TOKEN + "/sendMessage"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // Kontrakt portu: nieudana wysylka nie moze wywrocic przebiegu, ktory ja zlecil —
        // inaczej fill nie zostalby zapisany jako obsluzony i alert wracalby bez konca.
        assertThatCode(() -> publisher.send("cokolwiek")).doesNotThrowAnyException();
    }
}

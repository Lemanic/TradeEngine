package pl.tradeengine.adapter.inbound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.tradeengine.application.service.WebhookChainHarness;
import pl.tradeengine.application.service.WebhookProcessingService;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Timeframe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.tradeengine.TestFixtures.BTC;

/**
 * Cztery endpointy, kazdy z identycznym try/catch. Wzorzec jak w PortfolioControllerTest: bez
 * mocka serwisu — @TestConfiguration podaje <b>prawdziwy</b> WebhookProcessingService zlozony
 * z fake'ow, wiec jeden plik pokrywa droge od JSON-a az do publishera.
 *
 * <p>Najwazniejsze, co te testy utrwalaja, to zachowanie catch-all: <b>kazdy</b> wyjatek, nie
 * tylko zly payload, wychodzi jako 400. Dla TradingView 400 znaczy "twoja wiadomosc jest zla",
 * wiec awaria bazy po naszej stronie zdejmuje z niego powod do ponowienia — alert przepada.
 */
@WebMvcTest(TradingViewWebhookController.class)
@ActiveProfiles("test")
class TradingViewWebhookControllerTest {

    static final WebhookChainHarness HARNESS = WebhookChainHarness.silent();

    @TestConfiguration
    static class Beans {
        @Bean
        WebhookProcessingService webhookProcessingService() {
            return HARNESS.service;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HARNESS.clear();
    }

    // ─────────────────────────────────────────────────────────────
    // Poprawny payload → 202 i zdarzenie u scenariusza
    // ─────────────────────────────────────────────────────────────

    @Test
    void fvgWebhookIsAcceptedAndReachesTheScenario() throws Exception {
        mockMvc.perform(post("/webhooks/fvg").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"H4","signalType":"FVG_BULLISH",
                         "direction":"LONG","fvgLow":60000,"fvgHigh":61000,"fvgStatus":"CREATED"}"""))
                .andExpect(status().isAccepted());

        FvgCreatedEvent event = HARNESS.scenario.firstOfType(FvgCreatedEvent.class);
        assertThat(event.fvgZone().getTimeframe()).isEqualTo(Timeframe.H4);
        assertThat(event.fvgZone().getSymbol()).isEqualTo(BTC);
    }

    @Test
    void divergenceWebhookIsAcceptedAndReachesTheScenario() throws Exception {
        mockMvc.perform(post("/webhooks/divergence").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"M15","signalType":"DIVERGENCE","direction":"SHORT"}"""))
                .andExpect(status().isAccepted());

        assertThat(HARNESS.scenario.firstOfType(DivergenceDetectedEvent.class).signal().getTimeframe())
                .isEqualTo(Timeframe.M15);
    }

    @Test
    void priceWebhookIsAcceptedAndAlwaysEmitsTheCandle() throws Exception {
        mockMvc.perform(post("/webhooks/price").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"H1","signalType":"PRICE_UPDATE",
                         "currentHigh":60800,"currentLow":60200,"close":60500}"""))
                .andExpect(status().isAccepted());

        assertThat(HARNESS.scenario.firstOfType(PriceCandleEvent.class).candle().close())
                .isEqualByComparingTo("60500");
    }

    @Test
    void momentumWebhookOnLowerTimeframeRegistersASwingPoint() throws Exception {
        mockMvc.perform(post("/webhooks/momentum").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"H1","signal":"MOMENTUM_WAVE","direction":"BULLISH"}"""))
                .andExpect(status().isAccepted());

        assertThat(HARNESS.scenario.firstOfType(SwingPointDetectedEvent.class).type()).isEqualTo("SWING_LOW");
    }

    @Test
    void momentumWebhookOnDailyUpdatesBiasAndEmitsNothing() throws Exception {
        mockMvc.perform(post("/webhooks/momentum").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"D1","signal":"MOMENTUM_WAVE","direction":"BEARISH"}"""))
                .andExpect(status().isAccepted());

        assertThat(HARNESS.biasRepo.updates()).hasSize(1);
        assertThat(HARNESS.biasRepo.updates().get(0).status()).isEqualTo(BiasStatus.BEARISH);
        assertThat(HARNESS.scenario.seen()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Nieznany timeframe → 400 z catch-all, puste cialo odpowiedzi
    // ─────────────────────────────────────────────────────────────

    @Test
    void unknownTimeframeIs400OnEveryEndpoint() throws Exception {
        mockMvc.perform(post("/webhooks/fvg").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"M30","signalType":"FVG_BULLISH",
                         "direction":"LONG","fvgLow":60000,"fvgHigh":61000,"fvgStatus":"CREATED"}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/webhooks/divergence").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"M30","signalType":"DIVERGENCE","direction":"LONG"}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/webhooks/price").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"M30","signalType":"PRICE_UPDATE",
                         "currentHigh":60800,"currentLow":60200,"close":60500}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/webhooks/momentum").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"M30","signal":"MOMENTUM_WAVE","direction":"BULLISH"}"""))
                .andExpect(status().isBadRequest());

        // Nic nie poszlo dalej — 400 jest ostatnim slowem.
        assertThat(HARNESS.scenario.seen()).isEmpty();
    }

    @Test
    void badDirectionOrStatusIsAlso400_indistinguishableFromABadTimeframe() throws Exception {
        // Ciala odpowiedzi nie ma (ResponseEntity<Void>), wiec z punktu widzenia TradingView
        // wszystkie bledy mapowania sa tym samym zdarzeniem. Diagnoza jest tylko w logu.
        mockMvc.perform(post("/webhooks/fvg").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"H1","signalType":"FVG_BULLISH",
                         "direction":"BULLISH","fvgLow":60000,"fvgHigh":61000,"fvgStatus":"CREATED"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        mockMvc.perform(post("/webhooks/fvg").contentType(APPLICATION_JSON).content("""
                        {"symbol":"BTCUSDT","timeframe":"H1","signalType":"ORDER_BLOCK",
                         "direction":"LONG","fvgLow":60000,"fvgHigh":61000,"fvgStatus":"CREATED"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTimeframeIs400_becauseTheMapperThrowsOnNull() throws Exception {
        // Brak pola w rekordzie to null, a Timeframe.fromCode(null) rzuca NPE — zlapany przez
        // ten sam catch-all. Nie ma @Valid ani @NotNull, wiec walidacja nie istnieje.
        mockMvc.perform(post("/webhooks/divergence").contentType(APPLICATION_JSON)
                        .content("""
                                {"symbol":"BTCUSDT","signalType":"DIVERGENCE","direction":"LONG"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIs400FromSpringItself_notFromTheCatchAll() throws Exception {
        // Ta odpowiedz powstaje przed wejsciem do metody kontrolera, wiec serwis nie jest wolany.
        mockMvc.perform(post("/webhooks/price").contentType(APPLICATION_JSON).content("{\"symbol\":"))
                .andExpect(status().isBadRequest());

        assertThat(HARNESS.scenario.seen()).isEmpty();
    }
}

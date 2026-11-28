package pl.tradeengine.adapter.inbound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.service.AlertMuteService;
import pl.tradeengine.application.service.FakeSeenSymbols;
import pl.tradeengine.application.service.InMemoryAlertMuteRepository;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ksztalt JSON-a dla dashboardu, na prawdziwym serwisie nad fake'ami — bez mockow. */
@WebMvcTest(StrategyController.class)
@ActiveProfiles("test")
class StrategyControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");
    static final InMemoryAlertMuteRepository MUTES = new InMemoryAlertMuteRepository();

    @TestConfiguration
    static class Beans {

        @Bean
        AlertMuteService alertMuteService() {
            return new AlertMuteService(
                    TestFixtures.strategies(
                            TestFixtures.strategy("DOUBLE_DIVERGENCE_STRATEGY", true, Timeframe.H1, Timeframe.H4),
                            TestFixtures.strategy("FVG_LTF_CONFIRMATION", false, Timeframe.M5)),
                    MUTES, new FakeSeenSymbols("BTCUSDT", "TAOUSDT"), Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MUTES.clear();
    }

    @Test
    void listsSymbolsAndStrategiesWithTheirMutes() throws Exception {
        MUTES.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", new Symbol("TAOUSDT"), null, NOW, null));

        mvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbols[0]").value("BTCUSDT"))
                .andExpect(jsonPath("$.symbols[1]").value("TAOUSDT"))
                .andExpect(jsonPath("$.strategies[0].name").value("DOUBLE_DIVERGENCE_STRATEGY"))
                .andExpect(jsonPath("$.strategies[0].enabled").value(true))
                .andExpect(jsonPath("$.strategies[0].timeframes[1]").value("H4"))
                .andExpect(jsonPath("$.strategies[0].mutes[0].symbol").value("TAOUSDT"))
                .andExpect(jsonPath("$.strategies[0].mutes[0].timeframe").value(nullValue()))
                .andExpect(jsonPath("$.strategies[0].mutes[0].mutedAt").value("2026-11-28T09:15:00Z"))
                .andExpect(jsonPath("$.strategies[1].enabled").value(false))
                .andExpect(jsonPath("$.strategies[1].mutes").isEmpty());
    }

    @Test
    void putMutesThePair_andATickerWithPunctuationArrivesIntact() throws Exception {
        mvc.perform(put("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes")
                        .param("symbol", "CME_MINI:NQ1!")
                        .param("timeframe", "H1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("DOUBLE_DIVERGENCE_STRATEGY"))
                .andExpect(jsonPath("$.symbol").value("CME_MINI:NQ1!"))
                .andExpect(jsonPath("$.timeframe").value("H1"));

        assertThat(MUTES.findAll()).containsExactly(
                new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", new Symbol("CME_MINI:NQ1!"), Timeframe.H1, NOW, null));
    }

    @Test
    void putRecordsWhoMuted_fromTheCloudflareAccessHeader() throws Exception {
        mvc.perform(put("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes")
                        .param("symbol", "TAOUSDT")
                        .header("Cf-Access-Authenticated-User-Email", "owner@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mutedBy").value("owner@example.com"));
    }

    @Test
    void withoutTheHeaderMutedByIsNull_likeOnLocalhostOnProd() throws Exception {
        mvc.perform(put("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes").param("symbol", "TAOUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mutedBy").value(nullValue()));
    }

    @Test
    void putWithoutTimeframeMutesEveryTimeframe() throws Exception {
        mvc.perform(put("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes").param("symbol", "TAOUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframe").value(nullValue()));
    }

    @Test
    void deleteLiftsTheMute() throws Exception {
        MUTES.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", new Symbol("TAOUSDT"), null, NOW, null));

        mvc.perform(delete("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes").param("symbol", "TAOUSDT"))
                .andExpect(status().isNoContent());

        assertThat(MUTES.findAll()).isEmpty();
    }

    @Test
    void anUnknownStrategyIs404_withTheAvailableNames() throws Exception {
        mvc.perform(put("/api/strategies/GRINDER/mutes").param("symbol", "TAOUSDT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("unknown_strategy"))
                .andExpect(jsonPath("$.message").value(containsString("DOUBLE_DIVERGENCE_STRATEGY")));

        mvc.perform(delete("/api/strategies/GRINDER/mutes").param("symbol", "TAOUSDT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingSymbolIs400() throws Exception {
        mvc.perform(put("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes").param("symbol", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_symbol"));
    }

    @Test
    void anUnknownTimeframeIs400_ratherThanMutingEverything() throws Exception {
        // Literowka w interwale nie moze sie zamienic w "wszystkie interwaly".
        mvc.perform(put("/api/strategies/DOUBLE_DIVERGENCE_STRATEGY/mutes")
                        .param("symbol", "TAOUSDT")
                        .param("timeframe", "h1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_timeframe"));

        assertThat(MUTES.findAll()).isEmpty();
    }
}

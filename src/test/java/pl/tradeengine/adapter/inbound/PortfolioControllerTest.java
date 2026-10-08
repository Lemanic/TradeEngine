package pl.tradeengine.adapter.inbound;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.tradeengine.application.service.portfolio.InMemoryManualHoldingRepository;
import pl.tradeengine.application.service.portfolio.ManualHoldingService;
import pl.tradeengine.application.service.portfolio.PortfolioQueryService;
import pl.tradeengine.application.service.portfolio.PortfolioSnapshotService;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.portfolio.FakePriceProvider;
import pl.tradeengine.portfolio.InMemoryPortfolioSnapshotRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the wire shape against the examples in docs/portfolio-api.md. Uses the real query
 * service over an in-memory repository — no mocks, so the JSON comes out of the same path
 * production uses.
 */
@WebMvcTest(PortfolioController.class)
@ActiveProfiles("test")
class PortfolioControllerTest {

    private static final Instant TAKEN_AT = Instant.parse("2026-10-04T07:00:00Z");
    static final InMemoryPortfolioSnapshotRepository REPOSITORY = new InMemoryPortfolioSnapshotRepository();

    static final InMemoryManualHoldingRepository MANUAL_AMOUNTS = new InMemoryManualHoldingRepository();

    /** Dwie zadeklarowane pozycje, zegar staly, zeby updatedAt bylo przewidywalne. */
    static final ManualHoldingService MANUAL_HOLDINGS = new ManualHoldingService(
            List.of(new ManualHoldingService.Definition("tao-staking", "TAO staking", "bittensor",
                            "TAO", 22974, new BigDecimal("11.37")),
                    new ManualHoldingService.Definition("trezor-bch", "Trezor", "bitcoin-cash",
                            "BCH", 1831, new BigDecimal("0.744"))),
            MANUAL_AMOUNTS, Clock.fixed(TAKEN_AT, ZoneOffset.UTC));

    @TestConfiguration
    static class Beans {

        @Bean
        PortfolioQueryService portfolioQueryService() {
            return new PortfolioQueryService(REPOSITORY);
        }

        @Bean
        ManualHoldingService manualHoldingService() {
            return MANUAL_HOLDINGS;
        }

        @Bean
        PortfolioSnapshotService portfolioSnapshotService() {
            // no holdings source — takeSnapshot() writes nothing, which is the 503 path
            return new PortfolioSnapshotService(List.of(), new FakePriceProvider(), REPOSITORY,
                    Clock.fixed(TAKEN_AT, ZoneOffset.UTC), Duration.ofDays(7));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static PortfolioPosition position(String source, String network, String asset,
                                              String amount, String priceUsd, boolean stale) {
        BigDecimal units = new BigDecimal(amount);
        BigDecimal usd = new BigDecimal(priceUsd);
        BigDecimal pln = usd.multiply(new BigDecimal("3.7"));
        return new PortfolioPosition(null, source, network, asset, 1027, units, usd, pln,
                units.multiply(usd), units.multiply(pln), stale);
    }

    private static void store(Instant takenAt, String totalUsd, String totalPln, boolean complete,
                              PortfolioPosition... positions) {
        REPOSITORY.save(new PortfolioSnapshot(null, takenAt, new BigDecimal(totalUsd),
                new BigDecimal(totalPln), complete, List.of(positions)));
    }

    @Test
    void summaryHasTheShapeFromTheContract() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT.minusSeconds(24 * 3600), "9000", "33300", true);
        store(TAKEN_AT, "9001.35", "33305.00", true,
                position("Rabby", "ethereum", "ETH", "2.41", "3735.00", false));

        mockMvc.perform(get("/api/portfolio/summary").param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.takenAt").value("2026-10-04T07:00:00Z"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.total").value(9001.35))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.change.h24.abs").value(1.35))
                .andExpect(jsonPath("$.change.h24.pct").value(0.015))
                .andExpect(jsonPath("$.change.d7").value(nullValue()))
                .andExpect(jsonPath("$.change.d30").value(nullValue()))
                .andExpect(jsonPath("$.ath.value").value(9001.35))
                .andExpect(jsonPath("$.ath.at").value("2026-10-04T07:00:00Z"))
                .andExpect(jsonPath("$.sources[0].name").value("Rabby"))
                .andExpect(jsonPath("$.sources[0].value").value(9001.35))
                .andExpect(jsonPath("$.sources[0].pct").value(100.0))
                .andExpect(jsonPath("$.positions[0].source").value("Rabby"))
                .andExpect(jsonPath("$.positions[0].network").value("ethereum"))
                .andExpect(jsonPath("$.positions[0].asset").value("ETH"))
                .andExpect(jsonPath("$.positions[0].amount").value(2.41))
                .andExpect(jsonPath("$.positions[0].price").value(3735.00))
                .andExpect(jsonPath("$.positions[0].value").value(9001.35))
                .andExpect(jsonPath("$.positions[0].pct").value(100.0))
                .andExpect(jsonPath("$.positions[0].stale").value(false));
    }

    @Test
    void nullChangeKeepsItsKeysInsteadOfOmittingThem() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT, "100", "370", true);

        String body = mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // null means "unknown"; an omitted key would leave the UI guessing
        assertThat(body).contains("\"h24\":null").contains("\"d7\":null").contains("\"d30\":null");
    }

    @Test
    void amountsAreJsonNumbersNotStrings() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT, "141.17", "522.33", true,
                position("Rabby", "ethereum", "NPC", "6390.281180127170627929", "0.022091374", false));

        String body = mockMvc.perform(get("/api/portfolio/summary"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"amount\":6390.281180127170627929")
                .contains("\"price\":0.0220914")
                .doesNotContain("E+").doesNotContain("E-");
    }

    @Test
    void summaryDefaultsToUsd() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT, "100", "370", true);

        mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.total").value(100.00));
    }

    @Test
    void summaryInPlnReturnsThePlnTotals() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT, "100", "370", true);

        mockMvc.perform(get("/api/portfolio/summary").param("currency", "PLN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.total").value(370.00));
    }

    @Test
    void summaryIsNoContentWithoutAnySnapshot() throws Exception {
        REPOSITORY.clear();

        mockMvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void historyHasTheShapeFromTheContract() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT.minusSeconds(3600), "47862.31", "177000.00", true);
        store(TAKEN_AT, "47899.88", "177229.55", false);

        mockMvc.perform(get("/api/portfolio/history").param("range", "24H").param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24H"))
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.points[0].t").value("2026-10-04T06:00:00Z"))
                .andExpect(jsonPath("$.points[0].value").value(47862.31))
                .andExpect(jsonPath("$.points[0].complete").value(true))
                .andExpect(jsonPath("$.points[1].t").value("2026-10-04T07:00:00Z"))
                .andExpect(jsonPath("$.points[1].value").value(47899.88))
                .andExpect(jsonPath("$.points[1].complete").value(false));
    }

    @Test
    void historyReportsTheIntervalOfEachRange() throws Exception {
        REPOSITORY.clear();
        store(TAKEN_AT, "100", "370", true);

        for (String[] pair : new String[][]{{"24H", "1h"}, {"7D", "1h"}, {"30D", "4h"},
                {"90D", "1d"}, {"1Y", "1d"}, {"ALL", "1d"}}) {
            mockMvc.perform(get("/api/portfolio/history").param("range", pair[0]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.range").value(pair[0]))
                    .andExpect(jsonPath("$.interval").value(pair[1]));
        }
    }

    @Test
    void historyIsAnEmptyArrayNotNoContentWhenThereIsNothing() throws Exception {
        REPOSITORY.clear();

        mockMvc.perform(get("/api/portfolio/history").param("range", "30D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("30D"))
                .andExpect(jsonPath("$.interval").value("4h"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points").isEmpty());
    }

    @Test
    void rejectsAnUnknownRange() throws Exception {
        mockMvc.perform(get("/api/portfolio/history").param("range", "24h"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_range"))
                .andExpect(jsonPath("$.message").value(containsString("24H")));
    }

    @Test
    void rejectsAMissingRange() throws Exception {
        mockMvc.perform(get("/api/portfolio/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_range"));
    }

    @Test
    void rejectsAnUnknownCurrency() throws Exception {
        mockMvc.perform(get("/api/portfolio/summary").param("currency", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_currency"));

        mockMvc.perform(get("/api/portfolio/history").param("range", "24H").param("currency", "usd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_currency"));
    }

    @Test
    void manualSnapshotIsUnavailableWhenNothingCanBeWritten() throws Exception {
        mockMvc.perform(post("/api/portfolio/snapshots"))
                .andExpect(status().isServiceUnavailable());
    }

    // --- Ręczne pozycje: docs/portfolio-api.md, "GET/PUT /api/portfolio/manual-holdings" ---

    @Test
    void manualHoldingsListHasTheShapeFromTheContract() throws Exception {
        MANUAL_AMOUNTS.clear();
        MANUAL_HOLDINGS.seedMissing();

        mockMvc.perform(get("/api/portfolio/manual-holdings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("tao-staking"))
                .andExpect(jsonPath("$[0].source").value("TAO staking"))
                .andExpect(jsonPath("$[0].asset").value("TAO"))
                .andExpect(jsonPath("$[0].network").value("bittensor"))
                .andExpect(jsonPath("$[0].amount").value(11.37))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-10-04T07:00:00Z"))
                .andExpect(jsonPath("$[1].key").value("trezor-bch"))
                // rejestr tego, co wpisano — bez kwot i bez cen
                .andExpect(jsonPath("$[0].value").doesNotExist())
                .andExpect(jsonPath("$[0].price").doesNotExist());
    }

    @Test
    void putStoresTheAmountAndReturnsThePositionInTheSameShape() throws Exception {
        MANUAL_AMOUNTS.clear();
        MANUAL_HOLDINGS.seedMissing();

        mockMvc.perform(put("/api/portfolio/manual-holdings/tao-staking")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 29.4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("tao-staking"))
                .andExpect(jsonPath("$.asset").value("TAO"))
                .andExpect(jsonPath("$.amount").value(29.4))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-04T07:00:00Z"));

        mockMvc.perform(get("/api/portfolio/manual-holdings"))
                .andExpect(jsonPath("$[0].amount").value(29.4));
    }

    /** Ilości nie wolno zaokrąglać — to nie kwota. */
    @Test
    void putKeepsTheFullPrecisionOfTheAmount() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/trezor-bch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0.74412345678}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"amount\":0.74412345678")));
    }

    @Test
    void putAcceptsZeroBecauseItMeansNothingHeldHereNow() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/trezor-bch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0));

        mockMvc.perform(get("/api/portfolio/manual-holdings"))
                .andExpect(jsonPath("$[1].key").value("trezor-bch"));
    }

    @Test
    void putRejectsANegativeAmount() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/trezor-bch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_amount"));
    }

    @Test
    void putRejectsAMissingAmount() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/trezor-bch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_amount"));
    }

    @Test
    void putRejectsANonNumericAmount() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/trezor-bch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": \"duzo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_body"));
    }

    @Test
    void putRejectsNaN() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/trezor-bch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": NaN}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putOnAnUnknownKeyIs404() throws Exception {
        mockMvc.perform(put("/api/portfolio/manual-holdings/sztabka-zlota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("unknown_key"));
    }
}

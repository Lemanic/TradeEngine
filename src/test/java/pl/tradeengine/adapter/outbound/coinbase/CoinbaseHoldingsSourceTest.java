package pl.tradeengine.adapter.outbound.coinbase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.domain.model.portfolio.Holding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoinbaseHoldingsSourceTest {

    private static final String BASE_URL = "https://api.coinbase.test";
    private static final String ACCOUNTS = BASE_URL + "/api/v3/brokerage/accounts?limit=250";

    /** Klucz jednorazowy, taki jak w CoinbaseJwtTest — tylko po to, by podpis dal sie policzyc. */
    private static final String PEM = CoinbaseTestKeys.PRIVATE_PEM;

    private MockRestServiceServer server;
    private CoinbaseHoldingsSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        CoinbaseClient client = new CoinbaseClient(builder.build(), new CoinbaseJwt(
                "organizations/o/apiKeys/k", PEM,
                Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC)));
        source = new CoinbaseHoldingsSource("Coinbase", "coinbase", List.of(
                new CoinbaseHoldingsSource.Currency("USDC", "USDC", 3408, BigDecimal.ZERO),
                new CoinbaseHoldingsSource.Currency("FET", "FET", 3773, BigDecimal.ZERO),
                new CoinbaseHoldingsSource.Currency("BTC", "BTC", 1, new BigDecimal("0.0001"))),
                client);
    }

    private static String account(String currency, String available) {
        return account(currency, available, "0");
    }

    private static String account(String currency, String available, String hold) {
        return """
                {"uuid":"u-%1$s","name":"%1$s Wallet","currency":"%1$s",
                 "available_balance":{"value":"%2$s","currency":"%1$s"},
                 "hold":{"value":"%3$s","currency":"%1$s"}}"""
                .formatted(currency, available, hold);
    }

    @Test
    void readsWhitelistedBalancesAndSendsABearerToken() {
        server.expect(requestTo(ACCOUNTS))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ey")))
                .andRespond(withSuccess("""
                        {"accounts":[%s,%s],"has_next":false,"cursor":""}
                        """.formatted(account("USDC", "954.5530092345746"), account("FET", "59.1")),
                        MediaType.APPLICATION_JSON));

        List<Holding> holdings = source.fetchHoldings();

        assertThat(holdings)
                .extracting(Holding::source, Holding::network, Holding::asset, Holding::cmcId)
                .containsExactly(
                        tuple("Coinbase", "coinbase", "USDC", 3408),
                        tuple("Coinbase", "coinbase", "FET", 3773));
        assertThat(holdings.getFirst().amount().toPlainString()).isEqualTo("954.5530092345746");
        server.verify();
    }

    @Test
    void ignoresCurrenciesOutsideTheWhitelist() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s,%s],"has_next":false}
                        """.formatted(account("DOGE", "1000"), account("USDC", "5")),
                        MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings()).extracting(Holding::asset).containsExactly("USDC");
    }

    @Test
    void dropsDustBelowTheConfiguredMinimum() {
        // konto gieldowe zbiera pylek z oplat i konwersji; 7E-7 BTC to kilka centow
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s,%s],"has_next":false}
                        """.formatted(account("BTC", "7E-7"), account("USDC", "10")),
                        MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings()).extracting(Holding::asset).containsExactly("USDC");
    }

    @Test
    void keepsABalanceThatReachesTheMinimum() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":false}
                        """.formatted(account("BTC", "0.0001")), MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings()).extracting(Holding::asset).containsExactly("BTC");
    }

    @Test
    void countsFundsHeldAgainstAnOpenOrder() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":false}
                        """.formatted(account("USDC", "18.04", "1000.5")),
                        MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings())
                .extracting(Holding::asset, h -> h.amount().toPlainString())
                .containsExactly(tuple("USDC", "1018.54"));
    }

    @Test
    void keepsAPositionWhoseBalanceIsEntirelyLockedInAnOrder() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":false}
                        """.formatted(account("BTC", "0", "0.02")), MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings())
                .extracting(Holding::asset, h -> h.amount().toPlainString())
                .containsExactly(tuple("BTC", "0.02"));
    }

    @Test
    void appliesTheMinimumToTheSumRatherThanTheAvailablePartAlone() {
        // Dust: 0.00005 available + 0.00004 on hold = 0.00009, czyli pod minimum 0.0001.
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":false}
                        """.formatted(account("BTC", "0.00005", "0.00004")),
                        MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings()).isEmpty();
    }

    @Test
    void treatsAMissingHoldObjectAsZero() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[{"uuid":"u","name":"USDC Wallet","currency":"USDC",
                         "available_balance":{"value":"12.5","currency":"USDC"}}],
                         "has_next":false}
                        """, MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings())
                .extracting(Holding::asset, h -> h.amount().toPlainString())
                .containsExactly(tuple("USDC", "12.5"));
    }

    @Test
    void dropsZeroBalances() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":false}
                        """.formatted(account("USDC", "0")), MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings()).isEmpty();
    }

    @Test
    void followsTheCursorUntilCoinbaseSaysThereIsNoMore() {
        server.expect(requestTo(ACCOUNTS))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":true,"cursor":"strona-2"}
                        """.formatted(account("USDC", "10")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ACCOUNTS + "&cursor=strona-2"))
                .andRespond(withSuccess("""
                        {"accounts":[%s],"has_next":false,"cursor":""}
                        """.formatted(account("FET", "20")), MediaType.APPLICATION_JSON));

        assertThat(source.fetchHoldings()).extracting(Holding::asset).containsExactly("USDC", "FET");
        server.verify();
    }

    @Test
    void explainsAnUnauthorizedResponse() {
        server.expect(requestTo(ACCOUNTS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> source.fetchHoldings())
                .isInstanceOf(CoinbaseException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("key name");
    }

    @Test
    void propagatesOtherFailuresSoTheSnapshotCanCarryAmountsForward() {
        server.expect(requestTo(ACCOUNTS)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> source.fetchHoldings())
                .isInstanceOf(CoinbaseException.class)
                .hasMessageContaining("502");
    }

    @Test
    void ownsOnlyItsOwnNetwork() {
        assertThat(source.name()).isEqualTo("Coinbase");
        assertThat(source.networks()).containsExactly("coinbase");
    }
}

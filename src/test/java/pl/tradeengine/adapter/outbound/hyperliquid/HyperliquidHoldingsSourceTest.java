package pl.tradeengine.adapter.outbound.hyperliquid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.domain.model.portfolio.Holding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HyperliquidHoldingsSourceTest {

    private static final String BASE_URL = "https://api.hyperliquid.test";
    private static final String ADDRESS = "0xAbC0000000000000000000000000000000000001";

    /** Mapowania wzięte z fullName, które podaje samo API. */
    private static final List<HyperliquidHoldingsSource.SpotToken> WHITELIST = List.of(
            new HyperliquidHoldingsSource.SpotToken("USDC", "USDC", 3408),
            new HyperliquidHoldingsSource.SpotToken("PURR", "PURR", 34332),
            new HyperliquidHoldingsSource.SpotToken("UETH", "ETH", 1027),
            new HyperliquidHoldingsSource.SpotToken("HPENGU", "PENGU", 34466));

    private MockRestServiceServer server;
    private HyperliquidHoldingsSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        source = new HyperliquidHoldingsSource("Rabby", "hyperliquid", BASE_URL, ADDRESS,
                WHITELIST,
                new HyperliquidHoldingsSource.AssetRef("USD", 3408),
                new HyperliquidHoldingsSource.AssetRef("HYPE", 32196),
                new HyperliquidClient(builder.build()));
    }

    private void respond(String type, String body) {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers
                                .requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value(type))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void respondSpot(String balances) {
        respond("spotClearinghouseState", "{\"balances\":[" + balances + "]}");
    }

    @Test
    void reportsSpotPerpsAndStakingOnOneNetwork() {
        respondSpot("""
                {"coin":"USDC","token":0,"total":"2580.97624305","hold":"1925.4"},
                {"coin":"PURR","token":1,"total":"374.856","hold":"0.0"},
                {"coin":"HYPE","token":150,"total":"0.0","hold":"0.0"},
                {"coin":"UBTC","token":197,"total":"0.0","hold":"0.0"},
                {"coin":"UETH","token":221,"total":"0.040930316","hold":"0.0"},
                {"coin":"HPENGU","token":292,"total":"6663.44025831","hold":"0.0"}""");
        respond("clearinghouseState", "{\"marginSummary\":{\"accountValue\":\"464.338018\"}}");
        respond("delegatorSummary", "{\"delegated\":\"10.09603953\"}");

        List<Holding> holdings = source.fetchHoldings();

        assertThat(holdings)
                .extracting(Holding::source, Holding::network, Holding::asset, Holding::cmcId)
                .containsExactly(
                        tuple("Rabby", "hyperliquid", "USDC", 3408),     // spot
                        tuple("Rabby", "hyperliquid", "PURR", 34332),
                        tuple("Rabby", "hyperliquid", "ETH", 1027),      // UETH = Unit Ethereum
                        tuple("Rabby", "hyperliquid", "PENGU", 34466),   // HPENGU = Pudgy Penguins
                        tuple("Rabby", "hyperliquid", "USD", 3408),      // kapitał na perpach
                        tuple("Rabby", "hyperliquid", "HYPE", 32196));   // staking
        assertThat(holdings.get(0).amount()).isEqualByComparingTo("2580.97624305");
        assertThat(holdings.get(2).amount()).isEqualByComparingTo("0.040930316");
        assertThat(holdings.get(4).amount()).isEqualByComparingTo("464.338018");
        assertThat(holdings.get(5).amount()).isEqualByComparingTo("10.09603953");
        server.verify();
    }

    @Test
    void dropsZeroBalancesAndTokensOutsideTheWhitelist() {
        respondSpot("""
                {"coin":"USDC","token":0,"total":"0.0","hold":"0.0"},
                {"coin":"USDH","token":360,"total":"123.0","hold":"0.0"},
                {"coin":"USDT0","token":268,"total":"50.0","hold":"0.0"}""");
        respond("clearinghouseState", "{\"marginSummary\":{\"accountValue\":\"0.0\"}}");
        respond("delegatorSummary", "{\"delegated\":\"0.0\"}");

        assertThat(source.fetchHoldings()).isEmpty();
        server.verify();
    }

    @Test
    void omitsPerpEquityAndStakingWhenThereIsNone() {
        respondSpot("{\"coin\":\"USDC\",\"token\":0,\"total\":\"10.0\",\"hold\":\"0.0\"}");
        respond("clearinghouseState", "{\"marginSummary\":{\"accountValue\":\"0\"}}");
        respond("delegatorSummary", "{\"delegated\":\"0\"}");

        assertThat(source.fetchHoldings())
                .extracting(Holding::asset).containsExactly("USDC");
    }

    @Test
    void survivesAMissingMarginSummaryAndEmptyBalanceList() {
        respondSpot("");
        respond("clearinghouseState", "{}");
        respond("delegatorSummary", "{\"delegated\":\"2.5\"}");

        assertThat(source.fetchHoldings())
                .extracting(Holding::asset).containsExactly("HYPE");
    }

    @Test
    void countsADuplicatedTickerOnlyOnce() {
        // Hyperliquid pozwala, by kilka tokenów nosiło ten sam ticker
        respondSpot("""
                {"coin":"PURR","token":1,"total":"100.0","hold":"0.0"},
                {"coin":"PURR","token":999,"total":"7777.0","hold":"0.0"}""");
        respond("clearinghouseState", "{\"marginSummary\":{\"accountValue\":\"0\"}}");
        respond("delegatorSummary", "{\"delegated\":\"0\"}");

        assertThat(source.fetchHoldings()).singleElement()
                .satisfies(holding -> assertThat(holding.amount()).isEqualByComparingTo("100.0"));
    }

    @Test
    void propagatesFailureSoTheSnapshotCanCarryAmountsForward() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo(BASE_URL + "/info"))
                .andRespond(withSuccess("nie-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(source::fetchHoldings).isInstanceOf(HyperliquidException.class);
    }

    @Test
    void ownsOnlyItsOwnNetwork() {
        assertThat(source.name()).isEqualTo("Rabby");
        assertThat(source.networks()).containsExactly("hyperliquid");
    }
}

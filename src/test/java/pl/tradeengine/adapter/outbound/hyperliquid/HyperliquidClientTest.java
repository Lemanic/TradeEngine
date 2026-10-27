package pl.tradeengine.adapter.outbound.hyperliquid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HyperliquidClientTest {

    private static final String BASE_URL = "https://api.hyperliquid.test";
    private static final String ADDRESS = "0xAbC0000000000000000000000000000000000001";

    private MockRestServiceServer server;
    private HyperliquidClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HyperliquidClient(builder.build());
    }

    @Test
    void asksForThePerpAccountAndKeepsEquityExact() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("clearinghouseState"))
                .andExpect(jsonPath("$.user").value(ADDRESS))
                .andRespond(withSuccess("""
                        {"marginSummary":{"accountValue":"464.338018","totalNtlPos":"3241.84581"},
                         "withdrawable":"1.289854","assetPositions":[]}
                        """, MediaType.APPLICATION_JSON));

        var state = client.perpState(BASE_URL, ADDRESS);

        // equity arrives as a decimal string and must not pass through a double
        assertThat(state.marginSummary().accountValue().toPlainString()).isEqualTo("464.338018");
        server.verify();
    }

    @Test
    void readsSpotBalancesWithTheirTokenIds() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value("spotClearinghouseState"))
                .andRespond(withSuccess("""
                        {"balances":[
                          {"coin":"USDC","token":0,"total":"2580.97624305","hold":"1925.397614"},
                          {"coin":"HPENGU","token":292,"total":"6663.44025831","hold":"0.0"}]}
                        """, MediaType.APPLICATION_JSON));

        var state = client.spotState(BASE_URL, ADDRESS);

        assertThat(state.balances()).hasSize(2);
        assertThat(state.balances().getFirst().coin()).isEqualTo("USDC");
        assertThat(state.balances().getFirst().token()).isEqualTo(0);
        assertThat(state.balances().getLast().total().toPlainString()).isEqualTo("6663.44025831");
    }

    @Test
    void readsDelegatedStake() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value("delegatorSummary"))
                .andRespond(withSuccess("""
                        {"delegated":"10.09603953","undelegated":"0.0",
                         "totalPendingWithdrawal":"0.0","nPendingWithdrawals":0}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.stakingState(BASE_URL, ADDRESS).delegated())
                .isEqualByComparingTo("10.09603953");
    }

    @Test
    void asksForFillsByTimeWithTheWholeBody() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("userFillsByTime"))
                .andExpect(jsonPath("$.user").value(ADDRESS))
                .andExpect(jsonPath("$.startTime").value(1793620000000L))
                // Sklejanie po czasie zepsuloby dedup: jedno zlecenie w kilku kawalkach
                // dostawaloby za kazdym razem inny tid.
                .andExpect(jsonPath("$.aggregateByTime").value(false))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.userFillsByTime(BASE_URL, ADDRESS, 1793620000000L)).isEmpty();
        server.verify();
    }

    @Test
    void readsFillsAndKeepsPricesExact() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value("userFillsByTime"))
                .andRespond(withSuccess("""
                        [{"tid":1234567890123,"time":1793620331000,"coin":"BTC","px":"108450.5",
                          "sz":"0.01234567","side":"B","dir":"Open Long","closedPnl":"0.0",
                          "fee":"0.470213","feeToken":"USDC","oid":55512345,"hash":"0xabc",
                          "startPosition":"0.0","crossed":true,"builderFee":"0.01"}]
                        """, MediaType.APPLICATION_JSON));

        var fills = client.userFillsByTime(BASE_URL, ADDRESS, 0L);

        assertThat(fills).hasSize(1);
        assertThat(fills[0].tid()).isEqualTo(1234567890123L);
        assertThat(fills[0].px().toPlainString()).isEqualTo("108450.5");
        assertThat(fills[0].sz().toPlainString()).isEqualTo("0.01234567");
        assertThat(fills[0].side()).isEqualTo("B");
        assertThat(fills[0].dir()).isEqualTo("Open Long");
    }

    @Test
    void toleratesAFillWithoutTheOptionalFields() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andRespond(withSuccess("""
                        [{"tid":7,"time":1793620331000,"coin":"@107","px":"0.0121","sz":"1000",
                          "side":"A","dir":"Sell"}]
                        """, MediaType.APPLICATION_JSON));

        var fill = client.userFillsByTime(BASE_URL, ADDRESS, 0L)[0];

        assertThat(fill.closedPnl()).isNull();
        assertThat(fill.fee()).isNull();
        assertThat(fill.feeToken()).isNull();
        assertThat(fill.coin()).isEqualTo("@107");
    }

    @Test
    void readsTheSpotDictionaryWithoutAnAddress() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value("spotMeta"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andRespond(withSuccess("""
                        {"universe":[{"name":"@107","tokens":[292,0],"index":107,
                                      "isCanonical":false}],
                         "tokens":[{"name":"HPENGU","index":292,"szDecimals":0},
                                   {"name":"USDC","index":0}]}
                        """, MediaType.APPLICATION_JSON));

        var meta = client.spotMeta(BASE_URL);

        assertThat(meta.universe()).hasSize(1);
        assertThat(meta.universe().getFirst().name()).isEqualTo("@107");
        assertThat(meta.universe().getFirst().tokens()).containsExactly(292, 0);
        assertThat(meta.tokens().getFirst().name()).isEqualTo("HPENGU");
        server.verify();
    }

    @Test
    void failsOnHttpError() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.perpState(BASE_URL, ADDRESS))
                .isInstanceOf(HyperliquidException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("clearinghouseState");
    }

    @Test
    void failsOnAnEmptyBody() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.spotState(BASE_URL, ADDRESS))
                .isInstanceOf(HyperliquidException.class)
                .hasMessageContaining("empty body");
    }
}

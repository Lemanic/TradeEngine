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

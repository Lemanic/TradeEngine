package pl.tradeengine.adapter.outbound.blockbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BlockbookClientTest {

    private static final String BASE_URL = "https://btc1.trezor.test";
    private static final String XPUB = "zpubTESTONLY0000000000000000000000000000000000000000000000";
    private static final String USER_AGENT = "tradeengine/1.0";

    private MockRestServiceServer server;
    private BlockbookClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BlockbookClient(builder.build(), USER_AGENT);
    }

    @Test
    void readsTheXpubBalanceAsWholeCoins() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                // without a User-Agent the real server answers 403
                .andExpect(header("User-Agent", USER_AGENT))
                .andRespond(withSuccess("""
                        {"address":"%s","balance":"804707","totalReceived":"804707",
                         "totalSent":"0","txs":2,"usedTokens":1}
                        """.formatted(XPUB), MediaType.APPLICATION_JSON));

        BigDecimal balance = client.balance("bitcoin", BASE_URL, XPUB, 8);

        assertThat(balance).isEqualByComparingTo("0.00804707");
        server.verify();
    }

    @Test
    void readsAZeroBalance() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess("{\"balance\":\"0\",\"txs\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.balance("bitcoin", BASE_URL, XPUB, 8)).isEqualByComparingTo("0");
    }

    @Test
    void scalesBySmallestUnitOfTheChain() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess("{\"balance\":\"2150000000\"}", MediaType.APPLICATION_JSON));

        assertThat(client.balance("bitcoin-cash", BASE_URL, XPUB, 8)).isEqualByComparingTo("21.5");
    }

    @Test
    void failsOnHttpErrorWithoutQuotingTheUrl() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.balance("bitcoin", BASE_URL, XPUB, 8))
                .isInstanceOf(BlockbookException.class)
                .hasMessageContaining("bitcoin")
                .hasMessageContaining("503")
                // the xpub travels in the path; it must never reach a log line
                .hasMessageNotContaining(XPUB);
    }

    @Test
    void failsOnTheBotFilterAnswer() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("<!DOCTYPE html>"));

        assertThatThrownBy(() -> client.balance("bitcoin", BASE_URL, XPUB, 8))
                .isInstanceOf(BlockbookException.class)
                .hasMessageContaining("403")
                .hasMessageNotContaining(XPUB);
    }

    @Test
    void failsOnAnApiLevelError() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess("{\"error\":\"Invalid XPUB key\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.balance("bitcoin", BASE_URL, XPUB, 8))
                .isInstanceOf(BlockbookException.class)
                .hasMessageContaining("Invalid XPUB key")
                .hasMessageNotContaining(XPUB);
    }

    @Test
    void failsOnAMissingBalance() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess("{\"txs\":0}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.balance("bitcoin", BASE_URL, XPUB, 8))
                .isInstanceOf(BlockbookException.class)
                .hasMessageContaining("no balance");
    }

    @Test
    void failsOnANonNumericBalance() {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess("{\"balance\":\"0.008 BTC\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.balance("bitcoin", BASE_URL, XPUB, 8))
                .isInstanceOf(BlockbookException.class)
                .hasMessageContaining("non-numeric");
    }
}

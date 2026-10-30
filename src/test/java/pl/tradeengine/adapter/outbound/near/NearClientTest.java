package pl.tradeengine.adapter.outbound.near;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NearClientTest {

    private static final String RPC_URL = "https://rpc.near.test";
    private static final String API_URL = "https://api.near.test";
    private static final String ACCOUNT = "testonly.near";
    private static final String POOL = "astro-stakers.poolv1.test";

    private MockRestServiceServer server;
    private NearClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NearClient(builder.build());
    }

    /** NEAR returns a view call's JSON result as an array of byte values, not as a string. */
    private static String payload(String json) {
        StringBuilder bytes = new StringBuilder();
        for (byte value : json.getBytes(StandardCharsets.UTF_8)) {
            if (bytes.length() > 0) {
                bytes.append(',');
            }
            bytes.append(value & 0xFF);
        }
        return "[" + bytes + "]";
    }

    private void expectDiscovery(String body) {
        server.expect(requestTo(API_URL + "/v1/account/" + ACCOUNT + "/staking"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void discoversTheStakingPoolsOfTheAccount() {
        expectDiscovery("""
                {"account_id":"%s","pools":[
                  {"pool_id":"%s","last_update_block_height":222120566},
                  {"pool_id":"left-behind.poolv1.test","last_update_block_height":null}]}
                """.formatted(ACCOUNT, POOL));

        List<String> pools = client.stakingPools(API_URL, ACCOUNT);

        // A pool the account has left is kept: only its balance answers that reliably.
        assertThat(pools).containsExactly(POOL, "left-behind.poolv1.test");
        server.verify();
    }

    @Test
    void readsAStakedBalanceInWholeNear() {
        server.expect(requestTo(RPC_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.params.request_type").value("call_function"))
                .andExpect(jsonPath("$.params.account_id").value(POOL))
                .andExpect(jsonPath("$.params.method_name").value("get_account_staked_balance"))
                // the account id travels base64-encoded in the view call's arguments
                .andExpect(jsonPath("$.params.args_base64").value(Base64.getEncoder()
                        .encodeToString(("{\"account_id\":\"" + ACCOUNT + "\"}")
                                .getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"block_height":222262798,"logs":[],
                         "result":%s}}
                        """.formatted(payload("\"3120968202568262545057580\"")),
                        MediaType.APPLICATION_JSON));

        BigDecimal staked = client.stakedBalance(RPC_URL, POOL, ACCOUNT);

        // yoctoNEAR is 24 decimals, and the integer does not survive a double
        assertThat(staked).isEqualByComparingTo("3.120968202568262545057580");
        server.verify();
    }

    @Test
    void readsTheLiquidBalanceInWholeNear() {
        server.expect(requestTo(RPC_URL))
                .andExpect(jsonPath("$.params.request_type").value("view_account"))
                .andExpect(jsonPath("$.params.account_id").value(ACCOUNT))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"amount":"201321068219144500000000",
                         "locked":"0","storage_usage":182}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.liquidBalance(RPC_URL, ACCOUNT))
                .isEqualByComparingTo("0.2013210682191445");
    }

    @Test
    void readsAnEmptyPoolAsZeroRatherThanAFailure() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"result":%s}}
                        """.formatted(payload("\"0\"")), MediaType.APPLICATION_JSON));

        assertThat(client.stakedBalance(RPC_URL, POOL, ACCOUNT)).isEqualByComparingTo("0");
    }

    /**
     * The trap this module is built around: a rejected request comes back with HTTP 200, so
     * status-based handling alone would read a missing account as a successful empty answer.
     */
    @Test
    void failsOnARequestErrorDespiteHttp200() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","error":{"name":"HANDLER_ERROR",
                         "cause":{"name":"UNKNOWN_ACCOUNT","info":{}},"code":-32000,
                         "message":"Server error","data":"account does not exist while viewing"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.liquidBalance(RPC_URL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("UNKNOWN_ACCOUNT");
    }

    /** A contract-level failure hides one level deeper, inside {@code result}, also under 200. */
    @Test
    void failsOnAContractErrorInsideTheResultDespiteHttp200() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"block_height":222261426,"logs":[],
                         "error":"wasm execution failed with error: MethodResolveError(MethodNotFound)"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.stakedBalance(RPC_URL, POOL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("MethodNotFound")
                .hasMessageContaining(POOL);
    }

    @Test
    void failsOnAnHttpErrorFromPoolDiscoveryWithoutQuotingTheAccount() {
        server.expect(requestTo(API_URL + "/v1/account/" + ACCOUNT + "/staking"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.stakingPools(API_URL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("503")
                // the account id sits in the path, and it ties the owner to an on-chain identity
                .hasMessageNotContaining(ACCOUNT);
    }

    @Test
    void failsOnADiscoveryBodyWithoutPools() {
        expectDiscovery("{\"account_id\":\"" + ACCOUNT + "\"}");

        assertThatThrownBy(() -> client.stakingPools(API_URL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("empty body");
    }

    @Test
    void failsOnAMissingAmount() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"locked":"0"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.liquidBalance(RPC_URL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("no amount");
    }

    @Test
    void failsOnAnEmptyViewCallPayload() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"result":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.stakedBalance(RPC_URL, POOL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void failsOnANonNumericBalance() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":"1","result":{"amount":"3.12 NEAR"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.liquidBalance(RPC_URL, ACCOUNT))
                .isInstanceOf(NearRpcException.class)
                .hasMessageContaining("non-numeric");
    }
}

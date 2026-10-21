package pl.tradeengine.adapter.outbound.evm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvmBalanceClientTest {

    private static final String RPC_URL = "https://rpc.example/ethereum";
    private static final String HOLDER = "0xAbC0000000000000000000000000000000000001";
    private static final String CONTRACT = "0x8eD97a637A790Be1feff5e888d43629dc05408F6";

    private MockRestServiceServer server;
    private EvmBalanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EvmBalanceClient(builder.build());
    }

    @Test
    void readsNativeBalanceAsWholeCoins() {
        server.expect(requestTo(RPC_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.method").value("eth_getBalance"))
                .andExpect(jsonPath("$.params[0]").value(HOLDER.toLowerCase()))
                .andExpect(jsonPath("$.params[1]").value("latest"))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x21720a3b34b10000\"}",
                        MediaType.APPLICATION_JSON));

        BigDecimal balance = client.nativeBalance(RPC_URL, HOLDER);

        // 2 410 000 000 000 000 000 wei
        assertThat(balance).isEqualByComparingTo("2.41");
        server.verify();
    }

    @Test
    void encodesBalanceOfWithTheHolderPaddedToAWord() {
        server.expect(requestTo(RPC_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.method").value("eth_call"))
                .andExpect(jsonPath("$.params[0].to").value(CONTRACT.toLowerCase()))
                .andExpect(jsonPath("$.params[0].data").value(
                        "0x70a08231"
                                + "000000000000000000000000"
                                + "abc0000000000000000000000000000000000001"))
                .andExpect(jsonPath("$.params[1]").value("latest"))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,"
                                + "\"result\":\"0x0000000000000000000000000000000000000000000000005150ae84a8cdf00000\"}",
                        MediaType.APPLICATION_JSON));

        BigDecimal balance = client.tokenBalance(RPC_URL, CONTRACT, HOLDER, 18);

        assertThat(balance).isEqualByComparingTo("1500");
        server.verify();
    }

    @Test
    void scalesTokenBalanceByTheConfiguredDecimals() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x75bcd15\"}",
                        MediaType.APPLICATION_JSON));

        BigDecimal balance = client.tokenBalance(RPC_URL, CONTRACT, HOLDER, 6);

        // 123 456 789 at 6 decimals
        assertThat(balance).isEqualByComparingTo("123.456789");
    }

    @Test
    void readsAZeroBalance() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x0\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.nativeBalance(RPC_URL, HOLDER)).isEqualByComparingTo("0");
    }

    @Test
    void failsOnRpcError() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,"
                                + "\"error\":{\"code\":-32000,\"message\":\"execution reverted\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.nativeBalance(RPC_URL, HOLDER))
                .isInstanceOf(EvmRpcException.class)
                .hasMessageContaining("-32000")
                .hasMessageContaining("execution reverted");
    }

    @Test
    void failsOnHttpError() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.nativeBalance(RPC_URL, HOLDER))
                .isInstanceOf(EvmRpcException.class)
                .hasMessageContaining("eth_getBalance");
    }

    @Test
    void failsWhenTheCallHitsNoContractCode() {
        server.expect(requestTo(RPC_URL))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.tokenBalance(RPC_URL, CONTRACT, HOLDER, 18))
                .isInstanceOf(EvmRpcException.class)
                .hasMessageContaining("wrong contract address");
    }

    @Test
    void rejectsAMalformedAddressBeforeCallingOut() {
        assertThatThrownBy(() -> client.nativeBalance(RPC_URL, "0x1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20-byte hex address");
        server.verify();
    }

    @Test
    void sendsJsonRpcEnvelope() {
        server.expect(requestTo(RPC_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.nativeBalance(RPC_URL, HOLDER))
                .isEqualByComparingTo("0.000000000000000001");
    }
}

package pl.tradeengine.adapter.outbound.evm;

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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EvmHoldingsSourceTest {

    private static final String ETH_RPC = "https://rpc.example/ethereum";
    private static final String ARB_RPC = "https://rpc.example/arbitrum";
    private static final String ADDRESS = "0xAbC0000000000000000000000000000000000001";
    private static final String NPC = "0x8eD97a637A790Be1feff5e888d43629dc05408F6";

    private MockRestServiceServer server;
    private EvmBalanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new EvmBalanceClient(builder.build());
    }

    private void respond(String rpcUrl, String resultHex) {
        server.expect(once(), requestTo(rpcUrl))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"" + resultHex + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void readsNativeAndWhitelistedTokenBalancesUnderOneLabel() {
        respond(ETH_RPC, "0x21720a3b34b10000");                               // 2.41 native
        respond(ETH_RPC, "0x5150ae84a8cdf00000");                             // 1500 NPC

        EvmHoldingsSource source = new EvmHoldingsSource("Rabby", List.of(
                new EvmHoldingsSource.Target(ADDRESS, "ethereum", ETH_RPC, "ETH", 1027,
                        List.of(new EvmHoldingsSource.Token("NPC", NPC, 18, 27960)))), client);

        List<Holding> holdings = source.fetchHoldings();

        assertThat(holdings)
                .extracting(Holding::source, Holding::network, Holding::asset, Holding::cmcId)
                .containsExactly(
                        tuple("Rabby", "ethereum", "ETH", 1027),
                        tuple("Rabby", "ethereum", "NPC", 27960));
        assertThat(holdings.get(0).amount()).isEqualByComparingTo("2.41");
        assertThat(holdings.get(1).amount()).isEqualByComparingTo("1500");
        server.verify();
    }

    @Test
    void dropsZeroBalances() {
        respond(ETH_RPC, "0x0");    // no native
        respond(ETH_RPC, "0x0");    // no token

        EvmHoldingsSource source = new EvmHoldingsSource("Rabby", List.of(
                new EvmHoldingsSource.Target(ADDRESS, "ethereum", ETH_RPC, "ETH", 1027,
                        List.of(new EvmHoldingsSource.Token("NPC", NPC, 18, 27960)))), client);

        assertThat(source.fetchHoldings()).isEmpty();
        server.verify();
    }

    @Test
    void coversEveryConfiguredNetworkOfTheLabel() {
        respond(ETH_RPC, "0xde0b6b3a7640000");   // 1 ETH on ethereum
        respond(ARB_RPC, "0x21720a3b34b10000");  // 2.41 ETH on arbitrum

        EvmHoldingsSource source = new EvmHoldingsSource("Rabby", List.of(
                new EvmHoldingsSource.Target(ADDRESS, "ethereum", ETH_RPC, "ETH", 1027, List.of()),
                new EvmHoldingsSource.Target(ADDRESS, "arbitrum", ARB_RPC, "ETH", 1027, List.of())), client);

        assertThat(source.networks()).containsExactly("ethereum", "arbitrum");
        assertThat(source.fetchHoldings())
                .extracting(Holding::network, holding -> holding.amount().stripTrailingZeros().toPlainString())
                .containsExactly(tuple("ethereum", "1"), tuple("arbitrum", "2.41"));
    }

    @Test
    void propagatesRpcFailureSoTheSnapshotCanCarryAmountsForward() {
        server.expect(requestTo(ETH_RPC))
                .andRespond(withSuccess(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32603,\"message\":\"node down\"}}",
                        MediaType.APPLICATION_JSON));

        EvmHoldingsSource source = new EvmHoldingsSource("Trezor", List.of(
                new EvmHoldingsSource.Target(ADDRESS, "bsc", ETH_RPC, "BNB", 1839, List.of())), client);

        assertThatThrownBy(source::fetchHoldings)
                .isInstanceOf(EvmRpcException.class)
                .hasMessageContaining("node down");
    }

    @Test
    void exposesTheLabelAsItsName() {
        EvmHoldingsSource source = new EvmHoldingsSource("Trezor", List.of(
                new EvmHoldingsSource.Target(ADDRESS, "bsc", ETH_RPC, "BNB", 1839, List.of())), client);

        assertThat(source.name()).isEqualTo("Trezor");
        assertThat(source.networks()).containsExactly("bsc");
    }
}

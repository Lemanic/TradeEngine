package pl.tradeengine.adapter.outbound.blockbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.domain.model.portfolio.Holding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BlockbookHoldingsSourceTest {

    private static final String BASE_URL = "https://btc1.trezor.test";
    private static final String XPUB = "zpubTESTONLY0000000000000000000000000000000000000000000000";

    private MockRestServiceServer server;
    private BlockbookHoldingsSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new BlockbookHoldingsSource("Trezor", "bitcoin", BASE_URL, XPUB, "BTC", 1, 8,
                new BlockbookClient(builder.build(), "tradeengine/1.0"));
    }

    private void respond(String body) {
        server.expect(requestTo(BASE_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void reportsOneBitcoinHoldingUnderTheWalletLabel() {
        respond("{\"balance\":\"804707\"}");

        List<Holding> holdings = source.fetchHoldings();

        assertThat(holdings).singleElement().satisfies(holding -> {
            assertThat(holding.source()).isEqualTo("Trezor");
            assertThat(holding.network()).isEqualTo("bitcoin");
            assertThat(holding.asset()).isEqualTo("BTC");
            assertThat(holding.cmcId()).isEqualTo(1);
            assertThat(holding.amount()).isEqualByComparingTo("0.00804707");
        });
    }

    @Test
    void dropsAZeroBalance() {
        respond("{\"balance\":\"0\"}");

        assertThat(source.fetchHoldings()).isEmpty();
    }

    @Test
    void ownsOnlyItsOwnNetworkSoTheEvmHalfOfTheLabelIsUntouched() {
        assertThat(source.name()).isEqualTo("Trezor");
        assertThat(source.networks()).containsExactly("bitcoin");
    }

    @Test
    void propagatesFailureSoTheSnapshotCanCarryTheAmountForward() {
        respond("{\"error\":\"Invalid XPUB key\"}");

        assertThatThrownBy(source::fetchHoldings).isInstanceOf(BlockbookException.class);
    }
}

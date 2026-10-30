package pl.tradeengine.adapter.outbound.near;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.domain.model.portfolio.Holding;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NearHoldingsSourceTest {

    private static final String RPC_URL = "https://rpc.near.test";
    private static final String API_URL = "https://api.near.test";
    private static final String ACCOUNT = "testonly.near";

    private MockRestServiceServer server;
    private NearHoldingsSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new NearHoldingsSource("NEAR staking", "near", RPC_URL, API_URL, ACCOUNT,
                "NEAR", 6535, new NearClient(builder.build()));
    }

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

    private void expectPools(String... poolIds) {
        StringBuilder pools = new StringBuilder();
        for (String poolId : poolIds) {
            if (pools.length() > 0) {
                pools.append(',');
            }
            pools.append("{\"pool_id\":\"").append(poolId).append("\"}");
        }
        server.expect(requestTo(API_URL + "/v1/account/" + ACCOUNT + "/staking"))
                .andRespond(withSuccess("{\"pools\":[" + pools + "]}", MediaType.APPLICATION_JSON));
    }

    private void expectStaked(String yocto) {
        server.expect(requestTo(RPC_URL)).andRespond(withSuccess(
                "{\"result\":{\"result\":%s}}".formatted(payload("\"" + yocto + "\"")),
                MediaType.APPLICATION_JSON));
    }

    private void expectLiquid(String yocto) {
        server.expect(requestTo(RPC_URL)).andRespond(withSuccess(
                "{\"result\":{\"amount\":\"%s\",\"locked\":\"0\"}}".formatted(yocto),
                MediaType.APPLICATION_JSON));
    }

    @Test
    void reportsStakedAndLiquidNearAsOneHolding() {
        expectPools("astro-stakers.poolv1.test");
        expectStaked("3120968202568262545057580");
        expectLiquid("201321068219144500000000");

        List<Holding> holdings = source.fetchHoldings();

        assertThat(holdings).singleElement().satisfies(holding -> {
            assertThat(holding.source()).isEqualTo("NEAR staking");
            assertThat(holding.network()).isEqualTo("near");
            assertThat(holding.asset()).isEqualTo("NEAR");
            assertThat(holding.cmcId()).isEqualTo(6535);
            // one position, because positions aggregate on (source, network, asset, cmcId)
            assertThat(holding.amount()).isEqualByComparingTo("3.322289270787407045057580");
        });
        server.verify();
    }

    @Test
    void sumsEveryFundedPool() {
        expectPools("one.poolv1.test", "two.poolv1.test");
        expectStaked("1000000000000000000000000");
        expectStaked("2500000000000000000000000");
        expectLiquid("0");

        assertThat(source.fetchHoldings()).singleElement()
                .extracting(Holding::amount)
                .satisfies(amount -> assertThat(amount).isEqualByComparingTo("3.5"));
    }

    /** Discovery also lists pools the account has left; those answer with a zero balance. */
    @Test
    void skipsAPoolTheAccountNoLongerStakesIn() {
        expectPools("current.poolv1.test", "left-behind.poolv1.test");
        expectStaked("4000000000000000000000000");
        expectStaked("0");
        expectLiquid("0");

        assertThat(source.fetchHoldings()).singleElement()
                .extracting(Holding::amount)
                .satisfies(amount -> assertThat(amount).isEqualByComparingTo("4"));
    }

    @Test
    void reportsNothingWhenTheAccountHoldsNoNear() {
        expectPools("left-behind.poolv1.test");
        expectStaked("0");
        expectLiquid("0");

        assertThat(source.fetchHoldings()).isEmpty();
    }

    @Test
    void worksForAnAccountThatStakesNowhere() {
        expectPools();
        expectLiquid("500000000000000000000000");

        assertThat(source.fetchHoldings()).singleElement()
                .extracting(Holding::amount)
                .satisfies(amount -> assertThat(amount).isEqualByComparingTo("0.5"));
    }

    /**
     * A partial sum would understate the portfolio and look like a real drop on the chart, so
     * one unreadable pool has to fail the whole source — that is what hands the job over to
     * carry-forward, which keeps the previous amount and marks it stale.
     */
    @Test
    void failsWholesaleWhenOnePoolCannotBeRead() {
        expectPools("one.poolv1.test", "two.poolv1.test");
        expectStaked("1000000000000000000000000");
        server.expect(requestTo(RPC_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(source::fetchHoldings).isInstanceOf(NearRpcException.class);
    }

    @Test
    void propagatesADiscoveryFailureSoTheSnapshotCanCarryTheAmountForward() {
        server.expect(requestTo(API_URL + "/v1/account/" + ACCOUNT + "/staking"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(source::fetchHoldings).isInstanceOf(NearRpcException.class);
    }

    @Test
    void ownsOnlyItsOwnNetwork() {
        assertThat(source.name()).isEqualTo("NEAR staking");
        assertThat(source.networks()).containsExactly("near");
    }
}

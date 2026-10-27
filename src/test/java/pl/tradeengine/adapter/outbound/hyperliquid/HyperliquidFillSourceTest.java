package pl.tradeengine.adapter.outbound.hyperliquid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.domain.model.fill.TradeFill;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Mapowanie odpowiedzi API na fille. Najwazniejsze jest to, czego z samego JSON-a nie widac:
 * spotowy rynek nazywa sie {@code "@107"}, a owiniete tokeny nazywaja sie inaczej, niz sa warte.
 */
class HyperliquidFillSourceTest {

    private static final String BASE_URL = "https://api.hyperliquid.test";
    private static final String ADDRESS = "0xAbC0000000000000000000000000000000000001";
    private static final Instant SINCE = Instant.parse("2026-10-27T12:00:00Z");

    private static final List<HyperliquidHoldingsSource.SpotToken> WHITELIST = List.of(
            new HyperliquidHoldingsSource.SpotToken("UETH", "ETH", 1027),
            new HyperliquidHoldingsSource.SpotToken("HPENGU", "PENGU", 34466));

    /** Dwa rynki nazwane indeksem i jeden kanoniczny, jak w prawdziwym spotMeta. */
    private static final String SPOT_META = """
            {"universe":[
               {"name":"PURR/USDC","tokens":[1,0],"index":0,"isCanonical":true},
               {"name":"@107","tokens":[292,0],"index":107,"isCanonical":false},
               {"name":"@151","tokens":[221,0],"index":151,"isCanonical":false}],
             "tokens":[
               {"name":"USDC","index":0},
               {"name":"PURR","index":1},
               {"name":"HPENGU","index":292},
               {"name":"UETH","index":221}]}
            """;

    private MockRestServiceServer server;
    private HyperliquidFillSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        HyperliquidClient client = new HyperliquidClient(builder.build());
        source = new HyperliquidFillSource(BASE_URL, ADDRESS, client,
                new SpotAssetNames(client, BASE_URL), WHITELIST);
    }

    private void respond(String type, String body) {
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value(type))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void respondFills(String fills) {
        respond("userFillsByTime", "[" + fills + "]");
    }

    @Test
    void asksForFillsFromTheCursorWithoutAggregating() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value("userFillsByTime"))
                .andExpect(jsonPath("$.user").value(ADDRESS))
                .andExpect(jsonPath("$.startTime").value(SINCE.toEpochMilli()))
                .andExpect(jsonPath("$.aggregateByTime").value(false))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(source.fillsSince(SINCE)).isEmpty();
        server.verify();
    }

    @Test
    void mapsAPerpFillWithItsOwnWordsForTheDirection() {
        respondFills("""
                {"tid":900001,"time":1793620331000,"coin":"BTC","px":"108450.5","sz":"0.0123",
                 "side":"B","dir":"Open Long","closedPnl":"0.0","fee":"0.47","feeToken":"USDC",
                 "oid":5551,"hash":"0xabc","startPosition":"0.0","crossed":true}
                """);

        TradeFill fill = source.fillsSince(SINCE).getFirst();

        assertThat(fill.tid()).isEqualTo(900001L);
        assertThat(fill.time()).isEqualTo(Instant.ofEpochMilli(1793620331000L));
        assertThat(fill.displayName()).isEqualTo("BTC");
        assertThat(fill.side()).isEqualTo(TradeFill.Side.BUY);
        assertThat(fill.dir()).isEqualTo("Open Long");
        assertThat(fill.price()).isEqualByComparingTo("108450.5");
        assertThat(fill.size()).isEqualByComparingTo("0.0123");
        assertThat(fill.fee()).isEqualByComparingTo("0.47");
        assertThat(fill.spot()).isFalse();
    }

    @Test
    void resolvesASpotMarketIndexIntoTheAssetItIsWorth() {
        respondFills("""
                {"tid":900002,"time":1793620332000,"coin":"@107","px":"0.0121","sz":"1000",
                 "side":"A","dir":"Sell","closedPnl":"0.0","fee":"0.01","feeToken":"USDC"}
                """);
        respond("spotMeta", SPOT_META);

        TradeFill fill = source.fillsSince(SINCE).getFirst();

        // @107 to rynek tokena HPENGU, ktory jest wart tyle co PENGU — i tak go nazywamy.
        assertThat(fill.coin()).isEqualTo("@107");
        assertThat(fill.displayName()).isEqualTo("PENGU");
        assertThat(fill.spot()).isTrue();
        assertThat(fill.side()).isEqualTo(TradeFill.Side.SELL);
        server.verify();
    }

    @Test
    void readsTheSpotDictionaryOnceForSeveralFills() {
        respondFills("""
                {"tid":900003,"time":1793620333000,"coin":"@107","px":"0.0121","sz":"10",
                 "side":"B","dir":"Buy"},
                {"tid":900004,"time":1793620334000,"coin":"@151","px":"3000","sz":"1",
                 "side":"B","dir":"Buy"}
                """);
        respond("spotMeta", SPOT_META);

        assertThat(source.fillsSince(SINCE))
                .extracting(TradeFill::displayName)
                .containsExactly("PENGU", "ETH");
        // ExpectedCount.once() na spotMeta: drugi fill korzysta z tego, co juz w pamieci.
        server.verify();
    }

    @Test
    void readsACanonicalPairWithoutTheDictionary() {
        respondFills("""
                {"tid":900005,"time":1793620335000,"coin":"PURR/USDC","px":"0.21","sz":"100",
                 "side":"B","dir":"Buy"}
                """);

        TradeFill fill = source.fillsSince(SINCE).getFirst();

        assertThat(fill.displayName()).isEqualTo("PURR");
        assertThat(fill.spot()).isTrue();
        // Zadnego zapytania o spotMeta — nazwa pary mowi wszystko.
        server.verify();
    }

    @Test
    void stillReportsTheFillWhenTheDictionaryIsUnavailable() {
        respondFills("""
                {"tid":900006,"time":1793620336000,"coin":"@107","px":"0.0121","sz":"10",
                 "side":"B","dir":"Buy"}
                """);
        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/info"))
                .andExpect(jsonPath("$.type").value("spotMeta"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        TradeFill fill = source.fillsSince(SINCE).getFirst();

        // Brzydka nazwa jest lepsza niz brak alertu o zrealizowanym zleceniu.
        assertThat(fill.displayName()).isEqualTo("@107");
        assertThat(fill.spot()).isTrue();
    }

    @Test
    void defaultsMissingPnlAndFeeToZero() {
        respondFills("""
                {"tid":900007,"time":1793620337000,"coin":"HYPE","px":"42.5","sz":"3",
                 "side":"B","dir":"Open Long"}
                """);

        TradeFill fill = source.fillsSince(SINCE).getFirst();

        assertThat(fill.closedPnl()).isEqualByComparingTo("0");
        assertThat(fill.fee()).isEqualByComparingTo("0");
        assertThat(fill.feeToken()).isNull();
    }

    @Test
    void skipsAFillThatCouldNeverBeMarkedAsSeen() {
        respondFills("""
                {"time":1793620338000,"coin":"BTC","px":"108000","sz":"0.01","side":"B"},
                {"tid":900008,"time":1793620339000,"coin":"BTC","px":"108000","sz":"0.01",
                 "side":"B","dir":"Open Long"}
                """);

        // Bez tid nie da sie zapamietac, ze fill zostal obsluzony — alert o nim wracalby zawsze.
        assertThat(source.fillsSince(SINCE)).extracting(TradeFill::tid).containsExactly(900008L);
    }

    @Test
    void returnsTheOldestFillFirstAlthoughTheApiStartsWithTheNewest() {
        respondFills("""
                {"tid":3,"time":1793620340000,"coin":"BTC","px":"1","sz":"1","side":"B"},
                {"tid":1,"time":1793620320000,"coin":"BTC","px":"1","sz":"1","side":"B"},
                {"tid":2,"time":1793620330000,"coin":"BTC","px":"1","sz":"1","side":"B"}
                """);

        assertThat(source.fillsSince(SINCE)).extracting(TradeFill::tid).containsExactly(1L, 2L, 3L);
    }

    @Test
    void failureReachesTheCallerSoNothingIsMarkedAsSeen() {
        server.expect(requestTo(BASE_URL + "/info"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> source.fillsSince(SINCE))
                .isInstanceOf(HyperliquidException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("userFillsByTime");
    }
}

package pl.tradeengine.adapter.outbound.cmc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.domain.model.portfolio.AssetPrice;
import pl.tradeengine.domain.port.portfolio.PriceUnavailableException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CoinMarketCapPriceProviderTest {

    private static final String BASE_URL = "https://pro-api.coinmarketcap.test";
    private static final String API_KEY = "test-key";

    private MockRestServiceServer server;
    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private CoinMarketCapPriceProvider provider(String apiKey) {
        return new CoinMarketCapPriceProvider(builder.build(), apiKey);
    }

    private static String quotes(String currency, String ethPrice, String bnbPrice) {
        return """
                {
                  "status": {"error_code": 0, "error_message": null},
                  "data": {
                    "1027": {"id": 1027, "symbol": "ETH", "quote": {"%1$s": {"price": %2$s}}},
                    "1839": {"id": 1839, "symbol": "BNB", "quote": {"%1$s": {"price": %3$s}}}
                  }
                }
                """.formatted(currency, ethPrice, bnbPrice);
    }

    @Test
    void asksOncePerCurrencyWithEveryIdAndTheKeyInTheHeader() {
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027,1839&convert=USD"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("X-CMC_PRO_API_KEY", API_KEY))
                .andRespond(withSuccess(quotes("USD", "3735.0", "601.4"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027,1839&convert=PLN"))
                .andExpect(header("X-CMC_PRO_API_KEY", API_KEY))
                .andRespond(withSuccess(quotes("PLN", "13819.5", "2225.18"), MediaType.APPLICATION_JSON));

        Map<Integer, AssetPrice> prices = provider(API_KEY).pricesFor(Set.of(1027, 1839));

        assertThat(prices).hasSize(2);
        assertThat(prices.get(1027).usd()).isEqualByComparingTo("3735.0");
        assertThat(prices.get(1027).pln()).isEqualByComparingTo("13819.5");
        assertThat(prices.get(1839).usd()).isEqualByComparingTo("601.4");
        assertThat(prices.get(1839).pln()).isEqualByComparingTo("2225.18");
        server.verify();
    }

    @Test
    void leavesOutIdsMissingFromEitherCurrency() {
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027,27960&convert=USD"))
                .andRespond(withSuccess("""
                        {"status": {"error_code": 0},
                         "data": {"1027": {"id": 1027, "quote": {"USD": {"price": 3735.0}}},
                                  "27960": {"id": 27960, "quote": {"USD": {"price": 0.012}}}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027,27960&convert=PLN"))
                .andRespond(withSuccess("""
                        {"status": {"error_code": 0},
                         "data": {"1027": {"id": 1027, "quote": {"PLN": {"price": 13819.5}}}}}
                        """, MediaType.APPLICATION_JSON));

        Map<Integer, AssetPrice> prices = provider(API_KEY).pricesFor(Set.of(1027, 27960));

        assertThat(prices).containsOnlyKeys(1027);
    }

    @Test
    void failsOnUnauthorized() {
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027&convert=USD"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"status\":{\"error_code\":1001,\"error_message\":\"API key invalid\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider(API_KEY).pricesFor(Set.of(1027)))
                .isInstanceOf(PriceUnavailableException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("CMC_API_KEY");
    }

    @Test
    void failsOnRateLimit() {
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027&convert=USD"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider(API_KEY).pricesFor(Set.of(1027)))
                .isInstanceOf(PriceUnavailableException.class)
                .hasMessageContaining("429");
    }

    @Test
    void failsOnApiLevelErrorCode() {
        server.expect(requestTo(BASE_URL + "/v2/cryptocurrency/quotes/latest?id=1027&convert=USD"))
                .andRespond(withSuccess(
                        "{\"status\":{\"error_code\":1006,\"error_message\":\"plan not authorized\"},\"data\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider(API_KEY).pricesFor(Set.of(1027)))
                .isInstanceOf(PriceUnavailableException.class)
                .hasMessageContaining("1006")
                .hasMessageContaining("plan not authorized");
    }

    @Test
    void failsWithoutCallingOutWhenTheKeyIsMissing() {
        assertThatThrownBy(() -> provider("").pricesFor(Set.of(1027)))
                .isInstanceOf(PriceUnavailableException.class)
                .hasMessageContaining("CMC_API_KEY is not set");
        server.verify();
    }

    @Test
    void doesNotCallOutForAnEmptyIdSet() {
        assertThat(provider(API_KEY).pricesFor(Set.of())).isEmpty();
        server.verify();
    }
}

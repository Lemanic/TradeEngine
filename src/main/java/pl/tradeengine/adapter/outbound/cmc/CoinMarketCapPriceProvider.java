package pl.tradeengine.adapter.outbound.cmc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.tradeengine.domain.model.portfolio.AssetPrice;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.port.portfolio.PriceProvider;
import pl.tradeengine.domain.port.portfolio.PriceUnavailableException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prices from CoinMarketCap.
 *
 * <p>The Basic plan allows a single {@code convert} currency per request, so one snapshot costs
 * two calls — USD and PLN — each asking for every id at once.
 */
public class CoinMarketCapPriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinMarketCapPriceProvider.class);

    private static final String API_KEY_HEADER = "X-CMC_PRO_API_KEY";
    private static final String QUOTES_PATH = "/v2/cryptocurrency/quotes/latest";

    private final RestClient restClient;
    private final String apiKey;

    public CoinMarketCapPriceProvider(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public Map<Integer, AssetPrice> pricesFor(Set<Integer> cmcIds) {
        if (cmcIds.isEmpty()) {
            return Map.of();
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new PriceUnavailableException(
                    "CMC_API_KEY is not set — cannot price the portfolio");
        }

        String ids = cmcIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        Map<Integer, BigDecimal> usd = fetch(ids, FiatCurrency.USD);
        Map<Integer, BigDecimal> pln = fetch(ids, FiatCurrency.PLN);

        Map<Integer, AssetPrice> prices = new LinkedHashMap<>();
        for (Integer id : cmcIds) {
            BigDecimal usdPrice = usd.get(id);
            BigDecimal plnPrice = pln.get(id);
            if (usdPrice != null && plnPrice != null) {
                prices.put(id, new AssetPrice(usdPrice, plnPrice));
            } else {
                log.warn("CoinMarketCap returned no {} price for id {} — falling back to the last known price",
                        usdPrice == null ? "USD" : "PLN", id);
            }
        }
        return prices;
    }

    private Map<Integer, BigDecimal> fetch(String ids, FiatCurrency convert) {
        CmcQuotesResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path(QUOTES_PATH)
                            .queryParam("id", ids)
                            .queryParam("convert", convert.name())
                            .build())
                    .header(API_KEY_HEADER, apiKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw httpFailure(clientResponse.getStatusCode(), convert);
                    })
                    .body(CmcQuotesResponse.class);
        } catch (RestClientException e) {
            throw new PriceUnavailableException(
                    "CoinMarketCap request for convert=" + convert + " failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new PriceUnavailableException(
                    "CoinMarketCap returned an empty body for convert=" + convert);
        }
        Integer errorCode = response.status() == null ? null : response.status().errorCode();
        if (errorCode != null && errorCode != 0) {
            throw new PriceUnavailableException("CoinMarketCap error " + errorCode + " for convert="
                    + convert + ": " + response.status().errorMessage());
        }
        if (response.data() == null) {
            throw new PriceUnavailableException(
                    "CoinMarketCap returned no data for convert=" + convert);
        }

        Map<Integer, BigDecimal> prices = new LinkedHashMap<>();
        response.data().forEach((id, coin) -> {
            BigDecimal price = coin == null || coin.quote() == null ? null
                    : Optional.ofNullable(coin.quote().get(convert.name()))
                    .map(CmcQuotesResponse.Quote::price)
                    .orElse(null);
            if (price == null) {
                log.warn("CoinMarketCap response has no {} quote for id {}", convert, id);
                return;
            }
            try {
                prices.put(Integer.valueOf(id), price);
            } catch (NumberFormatException e) {
                log.warn("CoinMarketCap returned a non-numeric id '{}' — ignoring", id);
            }
        });
        return prices;
    }

    private static PriceUnavailableException httpFailure(HttpStatusCode status, FiatCurrency convert) {
        return switch (status.value()) {
            case 401 -> new PriceUnavailableException(
                    "CoinMarketCap rejected the API key (401) — check CMC_API_KEY");
            case 429 -> new PriceUnavailableException(
                    "CoinMarketCap rate limit reached (429) — snapshot skipped");
            default -> new PriceUnavailableException(
                    "CoinMarketCap returned HTTP " + status.value() + " for convert=" + convert);
        };
    }
}

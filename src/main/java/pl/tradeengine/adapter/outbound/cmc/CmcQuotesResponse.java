package pl.tradeengine.adapter.outbound.cmc;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response of {@code /v2/cryptocurrency/quotes/latest}. Queried by {@code id}, {@code data} is
 * an object keyed by the numeric id (querying by symbol would make it an array instead).
 */
public record CmcQuotesResponse(Status status, Map<String, Coin> data) {

    public record Status(
            @JsonProperty("error_code") Integer errorCode,
            @JsonProperty("error_message") String errorMessage
    ) {}

    public record Coin(Integer id, String symbol, Map<String, Quote> quote) {}

    public record Quote(BigDecimal price) {}
}

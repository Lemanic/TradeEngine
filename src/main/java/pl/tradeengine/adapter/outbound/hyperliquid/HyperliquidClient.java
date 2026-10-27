package pl.tradeengine.adapter.outbound.hyperliquid;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.ClearinghouseState;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.DelegatorSummary;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.Fill;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotClearinghouseState;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotMeta;

import java.util.Map;

/**
 * Reads an address's state from Hyperliquid's public {@code /info} endpoint — no API key, one
 * POST per query type, the address in the body.
 *
 * <p>Capital on Hyperliquid sits in three places that no single call covers, hence three of the
 * methods here: perp account equity, spot token balances and delegated (staked) HYPE. Vault
 * deposits and sub-accounts would be a fourth and fifth; this wallet has neither (checked:
 * {@code []} and {@code null}), so they are deliberately not read.
 *
 * <p>The remaining two serve the fill notifications rather than the portfolio: realised fills and
 * the spot market dictionary that turns a fill's {@code "@107"} into a readable ticker.
 */
public class HyperliquidClient {

    private static final String INFO_PATH = "/info";

    private final RestClient restClient;

    public HyperliquidClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Perp account equity in USD, after unrealised PnL. */
    public ClearinghouseState perpState(String baseUrl, String address) {
        return post(baseUrl, "clearinghouseState", address, ClearinghouseState.class);
    }

    /** Spot token balances. */
    public SpotClearinghouseState spotState(String baseUrl, String address) {
        return post(baseUrl, "spotClearinghouseState", address, SpotClearinghouseState.class);
    }

    /** Staked HYPE. */
    public DelegatorSummary stakingState(String baseUrl, String address) {
        return post(baseUrl, "delegatorSummary", address, DelegatorSummary.class);
    }

    /**
     * Zrealizowane zlecenia od {@code startTimeMs} <em>włącznie</em> — spot i perpy w jednej
     * odpowiedzi, maksymalnie 2000 na raz (API pamięta 10000 najnowszych).
     *
     * <p>{@code aggregateByTime: false} zostawia fille surowe, każdy z własnym {@code tid}.
     * Sklejanie po czasie zepsułoby dedup: jedno zlecenie zrealizowane w kilku kawałkach
     * dostawałoby za każdym razem inny identyfikator.
     */
    public Fill[] userFillsByTime(String baseUrl, String address, long startTimeMs) {
        return post(baseUrl, "userFillsByTime",
                Map.of("type", "userFillsByTime",
                        "user", address,
                        "startTime", startTimeMs,
                        "aggregateByTime", false),
                Fill[].class);
    }

    /** Słownik rynków spot — jedyne, po co go czytamy, to zamiana {@code "@107"} na tickera. */
    public SpotMeta spotMeta(String baseUrl) {
        return post(baseUrl, "spotMeta", Map.of("type", "spotMeta"), SpotMeta.class);
    }

    private <T> T post(String baseUrl, String type, String address, Class<T> responseType) {
        return post(baseUrl, type, Map.of("type", type, "user", address), responseType);
    }

    private <T> T post(String baseUrl, String type, Map<String, Object> body,
                       Class<T> responseType) {
        T response;
        try {
            response = restClient.post()
                    .uri(baseUrl + INFO_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new HyperliquidException("Hyperliquid answered HTTP "
                                + clientResponse.getStatusCode().value() + " for " + type);
                    })
                    .body(responseType);
        } catch (RestClientException e) {
            throw new HyperliquidException(
                    "Hyperliquid is unreachable for " + type + ": " + e.getMessage(), e);
        }
        if (response == null) {
            throw new HyperliquidException("Hyperliquid returned an empty body for " + type);
        }
        return response;
    }
}

package pl.tradeengine.adapter.outbound.hyperliquid;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.ClearinghouseState;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.DelegatorSummary;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotClearinghouseState;

import java.util.Map;

/**
 * Reads an address's state from Hyperliquid's public {@code /info} endpoint — no API key, one
 * POST per query type, the address in the body.
 *
 * <p>Capital on Hyperliquid sits in three places that no single call covers, hence three methods:
 * perp account equity, spot token balances and delegated (staked) HYPE. Vault deposits and
 * sub-accounts would be a fourth and fifth; this wallet has neither (checked: {@code []} and
 * {@code null}), so they are deliberately not read.
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

    private <T> T post(String baseUrl, String type, String address, Class<T> responseType) {
        T response;
        try {
            response = restClient.post()
                    .uri(baseUrl + INFO_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", type, "user", address))
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

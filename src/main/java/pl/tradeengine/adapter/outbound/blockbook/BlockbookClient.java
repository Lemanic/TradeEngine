package pl.tradeengine.adapter.outbound.blockbook;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Reads an extended-public-key balance from a Blockbook server (Trezor runs public ones).
 *
 * <p>Blockbook derives the addresses of the xpub itself, applies the gap limit and sums the
 * balance, which is what makes a {@code zpub} (BIP84) usable without any key derivation here.
 *
 * <p>Two things learned the hard way against {@code btc1.trezor.io}: the endpoint is behind a
 * bot filter that answers {@code 403} to a request without a {@code User-Agent}, and the xpub
 * travels in the URL path — so no failure message may ever quote the URL.
 */
public class BlockbookClient {

    private final RestClient restClient;
    private final String userAgent;

    public BlockbookClient(RestClient restClient, String userAgent) {
        this.restClient = restClient;
        this.userAgent = userAgent;
    }

    /**
     * Confirmed balance of {@code xpub}, in whole coins.
     *
     * @param network  name used for logging and error messages only
     * @param baseUrl  Blockbook root, e.g. {@code https://btc1.trezor.io}
     * @param decimals smallest-unit exponent of the chain (8 for Bitcoin)
     */
    public BigDecimal balance(String network, String baseUrl, String xpub, int decimals) {
        BlockbookXpubResponse response;
        try {
            response = restClient.get()
                    .uri(baseUrl + "/api/v2/xpub/{xpub}?details=basic", xpub)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new BlockbookException("Blockbook for " + network
                                + " answered HTTP " + clientResponse.getStatusCode().value());
                    })
                    .body(BlockbookXpubResponse.class);
        } catch (RestClientException e) {
            // The cause's message would quote the URL, and the URL carries the xpub.
            throw new BlockbookException("Blockbook for " + network + " is unreachable ("
                    + e.getClass().getSimpleName() + ")");
        }

        if (response == null) {
            throw new BlockbookException("Blockbook for " + network + " returned an empty body");
        }
        if (response.error() != null && !response.error().isBlank()) {
            throw new BlockbookException("Blockbook for " + network + " rejected the request: "
                    + response.error());
        }
        if (response.balance() == null || response.balance().isBlank()) {
            throw new BlockbookException("Blockbook for " + network + " returned no balance");
        }

        try {
            return new BigDecimal(new BigInteger(response.balance().trim())).movePointLeft(decimals);
        } catch (NumberFormatException e) {
            throw new BlockbookException("Blockbook for " + network
                    + " returned a non-numeric balance");
        }
    }
}

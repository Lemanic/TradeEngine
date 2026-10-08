package pl.tradeengine.adapter.outbound.coinbase;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseAccountsResponse.Account;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads account balances from Coinbase's Advanced Trade API.
 *
 * <p>Every request carries its own freshly signed token, because the {@code uri} claim binds a
 * token to one method and path and it expires after two minutes.
 */
public class CoinbaseClient {

    private static final String HOST = "api.coinbase.com";
    private static final String ACCOUNTS_PATH = "/api/v3/brokerage/accounts";
    private static final int PAGE_SIZE = 250;
    /** Guards against a cursor that never advances; 250 x 20 accounts is far beyond any wallet. */
    private static final int MAX_PAGES = 20;

    private final RestClient restClient;
    private final CoinbaseJwt jwt;

    public CoinbaseClient(RestClient restClient, CoinbaseJwt jwt) {
        this.restClient = restClient;
        this.jwt = jwt;
    }

    /** Every account, following the cursor until Coinbase says there is no more. */
    public List<Account> accounts() {
        List<Account> all = new ArrayList<>();
        String cursor = null;

        for (int page = 0; page < MAX_PAGES; page++) {
            CoinbaseAccountsResponse response = fetchPage(cursor);
            if (response.accounts() != null) {
                all.addAll(response.accounts());
            }
            boolean more = Boolean.TRUE.equals(response.hasNext())
                    && response.cursor() != null && !response.cursor().isBlank();
            if (!more) {
                return all;
            }
            cursor = response.cursor();
        }
        throw new CoinbaseException("Coinbase kept returning more pages than expected");
    }

    private CoinbaseAccountsResponse fetchPage(String cursor) {
        // The token signs the path without the query string.
        String token = jwt.tokenFor("GET", HOST, ACCOUNTS_PATH);
        try {
            CoinbaseAccountsResponse response = restClient.get()
                    .uri(builder -> {
                        builder.path(ACCOUNTS_PATH).queryParam("limit", PAGE_SIZE);
                        if (cursor != null) {
                            builder.queryParam("cursor", cursor);
                        }
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        int status = clientResponse.getStatusCode().value();
                        throw new CoinbaseException(status == 401
                                ? "Coinbase rejected the API key (401) — check the key name and "
                                        + "that the private key matches it"
                                : "Coinbase answered HTTP " + status + " for accounts");
                    })
                    .body(CoinbaseAccountsResponse.class);
            if (response == null) {
                throw new CoinbaseException("Coinbase returned an empty body for accounts");
            }
            return response;
        } catch (RestClientException e) {
            throw new CoinbaseException(
                    "Coinbase is unreachable: " + e.getClass().getSimpleName(), e);
        }
    }
}

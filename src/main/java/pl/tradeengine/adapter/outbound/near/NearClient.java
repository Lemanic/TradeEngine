package pl.tradeengine.adapter.outbound.near;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Reads a NEAR account's liquid balance and its native-staking balances — no API key.
 *
 * <p>Two different hosts, hence no {@code baseUrl} on the client: pool discovery is a REST GET
 * against FastNEAR's API, while balances come from JSON-RPC {@code query} calls that any NEAR
 * RPC provider serves. Staked amounts are not on the account — they live in the staking pool
 * contract, so each pool is asked for {@code get_account_staked_balance} separately.
 *
 * <p>Three things verified against mainnet that the shapes alone do not reveal:
 *
 * <ul>
 *   <li><b>NEAR reports failures under HTTP 200.</b> An unknown account answers {@code 200} with
 *       a populated {@code error} ({@code cause.name = UNKNOWN_ACCOUNT}), and a missing view
 *       method answers {@code 200} with an empty top-level {@code error} and the real failure
 *       <em>inside</em> {@code result}. Status-based error handling alone would read both as
 *       success, so both fields are checked on every call.
 *   <li><b>A view call's payload is a JSON array of byte values</b> ({@code [34,53,...]}), which
 *       decodes to UTF-8 and only then parses as JSON — a {@code U128} comes back quoted.
 *   <li><b>yoctoNEAR is 24 decimals</b>, not the 18 of the EVM chains, and the integers overflow
 *       a {@code double}, so the whole path stays on {@code BigInteger}/{@code BigDecimal}.
 * </ul>
 */
public class NearClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** NEAR quotes every amount in yoctoNEAR: 10^-24 NEAR. */
    public static final int YOCTO_DECIMALS = 24;

    private static final String QUERY = "query";
    private static final String STAKED_BALANCE = "get_account_staked_balance";
    private static final String VIEW_ACCOUNT = "view_account";
    private static final long REQUEST_ID = 1L;

    private final RestClient restClient;

    public NearClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Staking pools the account has delegated to, newest state first as FastNEAR returns them.
     *
     * <p>The list includes pools the account no longer stakes in — they are kept rather than
     * filtered on {@code last_update_block_height}, because only the pool's own balance answers
     * that reliably, and asking is one cheap call.
     *
     * @param apiBaseUrl FastNEAR API root, e.g. {@code https://api.fastnear.com}
     */
    public List<String> stakingPools(String apiBaseUrl, String accountId) {
        NearRpc.StakingPoolsResponse response;
        try {
            response = restClient.get()
                    .uri(apiBaseUrl + "/v1/account/{accountId}/staking", accountId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new NearRpcException("NEAR pool discovery answered HTTP "
                                + clientResponse.getStatusCode().value());
                    })
                    .body(NearRpc.StakingPoolsResponse.class);
        } catch (RestClientException e) {
            // The cause's message would quote the URL, and the account id sits in its path.
            throw new NearRpcException("NEAR pool discovery at " + apiBaseUrl
                    + " is unreachable (" + e.getClass().getSimpleName() + ")");
        }

        if (response == null || response.pools() == null) {
            throw new NearRpcException("NEAR pool discovery at " + apiBaseUrl
                    + " returned an empty body");
        }
        return response.pools().stream()
                .map(NearRpc.Pool::poolId)
                .filter(poolId -> poolId != null && !poolId.isBlank())
                .toList();
    }

    /** Unstaked, spendable balance of the account, in whole NEAR. */
    public BigDecimal liquidBalance(String rpcUrl, String accountId) {
        NearRpc.ViewAccountResponse response = query(rpcUrl, Map.of(
                "request_type", VIEW_ACCOUNT,
                "finality", "final",
                "account_id", accountId
        ), NearRpc.ViewAccountResponse.class);

        failOnRequestError(rpcUrl, VIEW_ACCOUNT, response.error());
        if (response.result() == null || isBlank(response.result().amount())) {
            throw new NearRpcException(VIEW_ACCOUNT + " at " + rpcUrl + " returned no amount");
        }
        return toNear(response.result().amount(), VIEW_ACCOUNT + " at " + rpcUrl);
    }

    /**
     * Balance the account has staked in one pool, in whole NEAR.
     *
     * <p>This number grows on its own: the pool contract books each epoch's rewards by raising
     * the price of a stake share, so the delegator's balance compounds without any action.
     */
    public BigDecimal stakedBalance(String rpcUrl, String poolId, String accountId) {
        NearRpc.CallFunctionResponse response = query(rpcUrl, Map.of(
                "request_type", "call_function",
                "finality", "final",
                "account_id", poolId,
                "method_name", STAKED_BALANCE,
                "args_base64", encodeArgs(accountId)
        ), NearRpc.CallFunctionResponse.class);

        failOnRequestError(rpcUrl, STAKED_BALANCE, response.error());
        NearRpc.CallFunctionResult result = response.result();
        if (result == null) {
            throw new NearRpcException(STAKED_BALANCE + " at " + rpcUrl + " returned no result");
        }
        // A contract-level failure arrives here, under HTTP 200 and an empty top-level error.
        if (!isBlank(result.error())) {
            throw new NearRpcException("Pool " + poolId + " rejected " + STAKED_BALANCE
                    + ": " + result.error());
        }
        if (result.result() == null || result.result().length == 0) {
            throw new NearRpcException("Pool " + poolId + " returned an empty "
                    + STAKED_BALANCE + " payload");
        }
        return toNear(decodeU128(result.result(), poolId), "Pool " + poolId);
    }

    private <T> T query(String rpcUrl, Map<String, Object> params, Class<T> responseType) {
        T response;
        try {
            response = restClient.post()
                    .uri(rpcUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new NearRpc.Request("2.0", REQUEST_ID, QUERY, params))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        throw new NearRpcException("NEAR RPC at " + rpcUrl + " answered HTTP "
                                + clientResponse.getStatusCode().value());
                    })
                    .body(responseType);
        } catch (RestClientException e) {
            // Safe to keep the cause here: the account id travels in the body, not the URL.
            throw new NearRpcException("NEAR RPC at " + rpcUrl + " failed: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new NearRpcException("NEAR RPC at " + rpcUrl + " returned an empty body");
        }
        return response;
    }

    /**
     * NEAR answers a rejected request with HTTP 200 and a populated {@code error}, so this is
     * the only thing standing between a bad account id and a silently missing position.
     */
    private static void failOnRequestError(String rpcUrl, String what, NearRpc.RpcError error) {
        if (error == null) {
            return;
        }
        String cause = error.cause() == null ? null : error.cause().name();
        throw new NearRpcException(what + " rejected by " + rpcUrl + ": "
                + (cause == null ? String.valueOf(error.name()) : error.name() + " / " + cause));
    }

    /** View-method arguments travel base64-encoded, so the JSON is built rather than spliced. */
    private static String encodeArgs(String accountId) {
        try {
            return Base64.getEncoder().encodeToString(
                    JSON.writeValueAsBytes(Map.of("account_id", accountId)));
        } catch (JsonProcessingException e) {
            throw new NearRpcException("Could not encode the NEAR view-call arguments", e);
        }
    }

    /** Byte values → UTF-8 → the quoted {@code U128} the contract returned. */
    private static String decodeU128(int[] payload, String poolId) {
        byte[] raw = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            raw[i] = (byte) payload[i];
        }
        try {
            return JSON.readValue(new String(raw, StandardCharsets.UTF_8), String.class);
        } catch (JsonProcessingException e) {
            throw new NearRpcException("Pool " + poolId + " returned an unreadable balance payload");
        }
    }

    private static BigDecimal toNear(String yocto, String what) {
        try {
            return new BigDecimal(new BigInteger(yocto.trim())).movePointLeft(YOCTO_DECIMALS);
        } catch (NumberFormatException e) {
            throw new NearRpcException(what + " returned a non-numeric balance");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

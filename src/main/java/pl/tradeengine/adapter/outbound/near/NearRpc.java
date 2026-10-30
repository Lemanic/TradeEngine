package pl.tradeengine.adapter.outbound.near;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * NEAR's JSON-RPC envelope and the two result shapes this module reads.
 *
 * <p>Deliberately separate from {@code JsonRpc} in the {@code evm} package: that one is
 * package-private and types {@code result} as a {@code String}, while NEAR returns an object —
 * and NEAR's {@code params} is a JSON object, not the positional array EVM nodes expect.
 */
final class NearRpc {

    private NearRpc() {}

    record Request(String jsonrpc, long id, String method, Map<String, Object> params) {}

    /** Response of {@code request_type: view_account}. */
    record ViewAccountResponse(ViewAccountResult result, RpcError error) {}

    /**
     * @param amount liquid balance in yoctoNEAR, as a decimal string
     * @param locked yoctoNEAR locked by a validator seat; {@code "0"} for a plain delegator
     */
    record ViewAccountResult(String amount, String locked) {}

    /** Response of {@code request_type: call_function}. */
    record CallFunctionResponse(CallFunctionResult result, RpcError error) {}

    /**
     * @param result the view method's return value as a JSON array of byte values, not a string
     * @param error  a contract-level failure, which travels <em>inside</em> {@code result} while
     *               the HTTP status stays 200 — e.g. calling a view method the contract lacks
     */
    record CallFunctionResult(int[] result, String error) {}

    /**
     * Request-level failure. NEAR reports these under HTTP 200, so {@code error} has to be
     * checked on every single response; {@code cause.name} carries the machine-readable reason
     * such as {@code UNKNOWN_ACCOUNT}.
     *
     * <p>{@code data} is deliberately not bound: it is a string for some errors and an object
     * for others, and {@code name} plus {@code cause.name} already say what went wrong.
     */
    record RpcError(String name, Cause cause) {}

    record Cause(String name) {}

    /** Response of FastNEAR's {@code GET /v1/account/{id}/staking}. */
    record StakingPoolsResponse(List<Pool> pools) {}

    /**
     * @param poolId        staking pool contract, e.g. {@code astro-stakers.poolv1.near}
     * @param lastUpdate    block height of the account's last change on that pool; {@code null}
     *                      for a pool it no longer uses. Not a filter — a pool is dropped on its
     *                      actual balance being zero, which is the only reliable answer.
     */
    record Pool(@JsonProperty("pool_id") String poolId,
                @JsonProperty("last_update_block_height") Long lastUpdate) {}
}

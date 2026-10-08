package pl.tradeengine.adapter.outbound.evm;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads balances over plain JSON-RPC — no API key, no vendor SDK.
 *
 * <p>Native balances come from {@code eth_getBalance}; ERC-20 balances from an {@code eth_call}
 * of {@code balanceOf(address)}, whose calldata is the 4-byte selector {@code 0x70a08231}
 * followed by the holder address left-padded to a 32-byte word. Both results are unsigned
 * integers in the asset's smallest unit, scaled down by the asset's decimals.
 */
public class EvmBalanceClient {

    /** {@code balanceOf(address)} — first 4 bytes of keccak256 of the signature. */
    private static final String BALANCE_OF_SELECTOR = "0x70a08231";

    /** Native coins (ETH, BNB) are quoted in wei. */
    public static final int NATIVE_DECIMALS = 18;

    private static final Pattern ADDRESS = Pattern.compile("(?i)0x[0-9a-f]{40}");
    private static final long REQUEST_ID = 1L;

    private final RestClient restClient;

    public EvmBalanceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Native coin balance of {@code address}, in whole coins. */
    public BigDecimal nativeBalance(String rpcUrl, String address) {
        String result = call(rpcUrl, "eth_getBalance", List.of(normalize(address), "latest"));
        return scale(decodeQuantity(result), NATIVE_DECIMALS);
    }

    /** ERC-20 balance of {@code holder} on {@code contract}, in whole tokens. */
    public BigDecimal tokenBalance(String rpcUrl, String contract, String holder, int decimals) {
        Map<String, String> callObject = Map.of(
                "to", normalize(contract),
                "data", BALANCE_OF_SELECTOR + toWord(holder)
        );
        String result = call(rpcUrl, "eth_call", List.of(callObject, "latest"));
        return scale(decodeQuantity(result), decimals);
    }

    private String call(String rpcUrl, String method, List<Object> params) {
        JsonRpc.Response response;
        try {
            response = restClient.post()
                    .uri(rpcUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new JsonRpc.Request("2.0", REQUEST_ID, method, params))
                    .retrieve()
                    .body(JsonRpc.Response.class);
        } catch (RestClientException e) {
            throw new EvmRpcException(method + " to " + rpcUrl + " failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new EvmRpcException(method + " to " + rpcUrl + " returned an empty body");
        }
        if (response.error() != null) {
            throw new EvmRpcException(method + " rejected by " + rpcUrl + ": "
                    + response.error().code() + " " + response.error().message());
        }
        if (response.result() == null) {
            throw new EvmRpcException(method + " to " + rpcUrl + " returned no result");
        }
        return response.result();
    }

    /**
     * Decodes a hex quantity. An empty {@code 0x} means the call hit no contract code, which is
     * a misconfigured contract address rather than a zero balance — so it fails loudly.
     */
    private static BigInteger decodeQuantity(String hex) {
        String digits = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (digits.isBlank()) {
            throw new EvmRpcException("Node returned an empty result ('" + hex + "') — wrong contract address?");
        }
        try {
            return new BigInteger(digits, 16);
        } catch (NumberFormatException e) {
            throw new EvmRpcException("Node returned a non-hex result: '" + hex + "'", e);
        }
    }

    private static BigDecimal scale(BigInteger raw, int decimals) {
        return new BigDecimal(raw).movePointLeft(decimals);
    }

    private static String normalize(String address) {
        if (address == null || !ADDRESS.matcher(address).matches()) {
            throw new IllegalArgumentException("Not a 20-byte hex address: '" + address + "'");
        }
        return address.toLowerCase();
    }

    /** Left-pads an address to a 32-byte ABI word (24 zero nibbles + 40 address nibbles). */
    private static String toWord(String address) {
        return "0".repeat(24) + normalize(address).substring(2);
    }
}

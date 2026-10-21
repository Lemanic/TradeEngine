package pl.tradeengine.adapter.outbound.coinbase;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response of {@code GET /api/v3/brokerage/accounts}. Only the fields this module needs;
 * unknown ones are ignored.
 *
 * @param hasNext  Jackson maps {@code has_next} to this via the relaxed snake-case setting? No —
 *                 see {@code @JsonProperty} on the component.
 */
public record CoinbaseAccountsResponse(
        List<Account> accounts,
        @com.fasterxml.jackson.annotation.JsonProperty("has_next") Boolean hasNext,
        String cursor
) {

    /** {@code available_balance} excludes amounts on hold against open orders. */
    public record Account(
            String currency,
            @com.fasterxml.jackson.annotation.JsonProperty("available_balance") Balance availableBalance,
            Balance hold
    ) {}

    public record Balance(BigDecimal value, String currency) {}
}

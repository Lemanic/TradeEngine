package pl.tradeengine.adapter.outbound.hyperliquid;

import java.math.BigDecimal;
import java.util.List;

/**
 * The slices of Hyperliquid's {@code /info} responses this module needs. Numbers arrive as
 * decimal strings and Jackson converts them to {@link BigDecimal} without going through a
 * {@code double}.
 */
public final class HyperliquidResponses {

    private HyperliquidResponses() {}

    /**
     * Perp account. {@code accountValue} is the account's equity in USD <em>after</em> unrealised
     * PnL — not a token balance, which is why it needs its own treatment when mapped to a holding.
     */
    public record ClearinghouseState(MarginSummary marginSummary) {}

    public record MarginSummary(BigDecimal accountValue) {}

    /** Spot balances. {@code total} includes amounts held against open orders. */
    public record SpotClearinghouseState(List<SpotBalance> balances) {}

    public record SpotBalance(String coin, Integer token, BigDecimal total) {}

    /** Staking. {@code delegated} is the amount currently delegated to validators. */
    public record DelegatorSummary(BigDecimal delegated) {}
}

package pl.tradeengine.backtest.trade;

/** What happened to one long signal. Only {@link #TRADED} became a position. */
public enum SignalOutcome {
    /** Passed the planner; becomes {@link #TRADED} or {@link #GAP_SKIPPED} at the next open. */
    PLANNED,
    TRADED,
    /** No open HTF FVG above price in the current leg — flagged for manual review. */
    NO_TARGET,
    RR_TOO_LOW,
    /** The next bar opened beyond SL or TP, so the planned trade no longer exists. */
    GAP_SKIPPED,
    /** Limit order never filled within its time to live. */
    LIMIT_EXPIRED,
    /** Price reached TP before the limit order filled — the move left without us. */
    LIMIT_MISSED,
    /** One position at a time: a signal during an open trade is ignored. */
    POSITION_OPEN,
    /** Signal bar closed on the low of the SL window — zero risk, no sizing possible. */
    NO_RISK,
    /** Entry waits for an M15 MSS in the trade's direction, and none came within its time to live. */
    NO_MSS,
    /** The M15 MSS came, but no M15 or M5 FVG lay between SL and price to rest the limit on. */
    NO_ENTRY_ZONE,
    /** Price reached SL while the entry was still waiting for its M15 MSS. */
    INVALIDATED,
    WARMUP
}

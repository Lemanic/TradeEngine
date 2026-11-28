package pl.tradeengine.domain.model;

import java.time.Instant;

/**
 * A muted (strategy, symbol) pair, optionally narrowed to one timeframe.
 *
 * <p>Muting suppresses delivery only: the scenario still receives every event and still produces
 * the alert, which {@code AlertDispatchService} then drops. That is what makes unmuting safe —
 * the strategy never lost its context, so it works correctly from the very next event.
 *
 * @param timeframe {@code null} means every timeframe of that strategy
 * @param mutedBy   who set it, as Cloudflare Access reported; {@code null} when the request did not
 *                  cross Access (localhost on the prod machine). Informational only — never use it
 *                  to decide anything, the header is trivial to forge from behind the tunnel.
 */
public record AlertMute(String strategy, Symbol symbol, Timeframe timeframe, Instant mutedAt,
                        String mutedBy) {

    public boolean covers(AlertToSend alert) {
        return strategy.equals(alert.getScenarioName())
                && symbol.equals(alert.getSymbol())
                && (timeframe == null || timeframe == alert.getTimeframe());
    }

    /** Same rule, regardless of when it was set — muting twice must not create a second one. */
    public boolean sameRuleAs(String otherStrategy, Symbol otherSymbol, Timeframe otherTimeframe) {
        return strategy.equals(otherStrategy) && symbol.equals(otherSymbol) && timeframe == otherTimeframe;
    }
}

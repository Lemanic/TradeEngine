package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

/** Muted (strategy, symbol, timeframe) rules. A rule is identified by those three alone. */
public interface AlertMuteRepository {

    /** Read on every dispatch that carries alerts, so a change applies without a restart. */
    List<AlertMute> findAll();

    /** Inserts, or replaces the rule with the same strategy, symbol and timeframe. */
    AlertMute save(AlertMute mute);

    /** Removes exactly that rule; a {@code null} timeframe addresses the all-timeframes rule only. */
    void delete(String strategy, Symbol symbol, Timeframe timeframe);
}

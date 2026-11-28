package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.Symbol;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Symbols the webhooks have recently talked about. {@link Symbol} is a free string, not an enum,
 * so this is the only place the application can learn which pairs exist at all.
 */
public interface SeenSymbols {

    Set<Symbol> since(ZonedDateTime since);
}

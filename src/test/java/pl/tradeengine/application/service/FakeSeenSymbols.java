package pl.tradeengine.application.service;

import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.port.SeenSymbols;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Zwraca to, co mu dano, i zapamietuje, o jaki moment pytano — okno 30 dni liczy serwis, wiec
 * test musi widziec, od kiedy zapytal.
 */
public class FakeSeenSymbols implements SeenSymbols {

    private final Set<Symbol> symbols = new LinkedHashSet<>();
    private ZonedDateTime lastAskedSince;

    public FakeSeenSymbols(String... codes) {
        for (String code : codes) {
            symbols.add(new Symbol(code));
        }
    }

    @Override
    public Set<Symbol> since(ZonedDateTime since) {
        lastAskedSince = since;
        return Set.copyOf(symbols);
    }

    public ZonedDateTime lastAskedSince() {
        return lastAskedSince;
    }
}

package pl.tradeengine.domain.port.fill;

import pl.tradeengine.domain.model.fill.TradeFill;

import java.time.Instant;
import java.util.List;

/** Zrealizowane zlecenia odczytane z giełdy. */
public interface TradeFillSource {

    /**
     * Fille od {@code since} (włącznie), posortowane od najstarszego.
     *
     * <p>Granica jest domknięta, bo taka jest w API Hyperliquid — ostatni już obsłużony fill
     * wraca więc przy każdym przebiegu i odsiewa go dopiero dedup po {@code tid}.
     *
     * <p>Implementacje wyrzucają wyjątek, gdy giełda jest nieosiągalna; przebieg nie zapisuje
     * wtedy niczego i próbuje ponownie za chwilę.
     */
    List<TradeFill> fillsSince(Instant since);
}

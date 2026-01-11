package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

public interface BiasRepository {
    void updateBias(Symbol symbol, Timeframe timeframe, BiasStatus status, String reason);
    BiasStatus getBias(Symbol symbol, Timeframe timeframe);
}

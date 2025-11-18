package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.util.List;

public interface FvgRepository {
    FvgZone save(FvgZone fvgZone);

    List<FvgZone> findIntersectingOpenFvgs(Symbol symbol, Timeframe timeframe, double price);

    void updateStatus(Long fvgId, FvgStatus newStatus);
}

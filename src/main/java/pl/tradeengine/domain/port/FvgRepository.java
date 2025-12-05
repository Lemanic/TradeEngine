package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.model.Direction;

import java.math.BigDecimal;
import java.util.List;

public interface FvgRepository {
    FvgZone save(FvgZone fvgZone);

    List<FvgZone> findIntersectingOpenFvgs(Symbol symbol, BigDecimal low, BigDecimal high);

    void updateStatus(Long fvgId, FvgStatus newStatus);

    List<FvgZone> findTouched(Symbol symbol, Timeframe timeframe);

    List<FvgZone> findActiveForSymbolAndDirectionOnHigherTf(
            Symbol symbol,
            Direction direction,
            List<FvgStatus> statuses,
            List<Timeframe> timeframes
    );

}

package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.StoredSwingPoint;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

public interface SwingPointRepository {
    void save(Symbol symbol, Timeframe timeframe, String type, java.math.BigDecimal price, ZonedDateTime detectedAt);

    List<StoredSwingPoint> findRecentSwings(Symbol symbol, Timeframe timeframe, String type, ZonedDateTime since);

    Direction getLastSwingDirection(Symbol symbol, Timeframe timeframe);
}

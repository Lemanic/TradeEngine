package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.AlertMode;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.model.Direction;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
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

    void markTouched(Long fvgId, ZonedDateTime touchedAt);

    void setLeftZoneAt(Long fvgId, ZonedDateTime leftZoneAt);

    void setAlertMode(Long fvgId, AlertMode mode);

    void resumeArmed(Long fvgId);

    void markFilled(Long fvgId, ZonedDateTime filledAt, ZonedDateTime expiresAt);

    int consumeExpiredFilled(ZonedDateTime now);

    List<FvgZone> findTouchedForSymbolOnTimeframes(
            Symbol symbol,
            List<Timeframe> timeframes
    );

}

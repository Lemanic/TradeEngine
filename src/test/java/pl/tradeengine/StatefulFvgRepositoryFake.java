package pl.tradeengine;

import pl.tradeengine.domain.model.AlertMode;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.FvgRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Stateful in-memory FvgRepository for unit tests of state transitions.
 * Unlike {@code backtest.repository.InMemoryFvgRepository} (which leaves several
 * methods as no-ops), this fake faithfully mutates state for setLeftZoneAt,
 * setAlertMode, resumeArmed, consumeExpiredFilled and findTouchedForSymbolOnTimeframes.
 * Mutates the stored FvgZone in place via setters.
 */
public class StatefulFvgRepositoryFake implements FvgRepository {

    private final Map<Long, FvgZone> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public FvgZone save(FvgZone fvg) {
        Long id = fvg.getId() != null ? fvg.getId() : idGenerator.getAndIncrement();
        fvg.setId(id);
        store.put(id, fvg);
        return fvg;
    }

    @Override
    public Optional<FvgZone> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<FvgZone> findIntersectingOpenFvgs(Symbol symbol, BigDecimal low, BigDecimal high) {
        return store.values().stream()
                .filter(f -> f.getSymbol().equals(symbol))
                .filter(f -> f.getStatus() != FvgStatus.CONSUMED)
                .filter(f -> f.getUpperPrice().compareTo(low) >= 0
                          && f.getLowerPrice().compareTo(high) <= 0)
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(Long fvgId, FvgStatus newStatus) {
        FvgZone f = store.get(fvgId);
        if (f != null) f.setStatus(newStatus);
    }

    @Override
    public List<FvgZone> findTouched(Symbol symbol, Timeframe timeframe) {
        return store.values().stream()
                .filter(f -> f.getSymbol().equals(symbol))
                .filter(f -> f.getTimeframe() == timeframe)
                .filter(f -> f.getStatus() == FvgStatus.TOUCHED)
                .collect(Collectors.toList());
    }

    @Override
    public List<FvgZone> findActiveForSymbolAndDirectionOnHigherTf(
            Symbol symbol, Direction direction, List<FvgStatus> statuses, List<Timeframe> timeframes) {
        return store.values().stream()
                .filter(f -> f.getSymbol().equals(symbol))
                .filter(f -> f.getDirection() == direction)
                .filter(f -> statuses.contains(f.getStatus()))
                .filter(f -> timeframes.contains(f.getTimeframe()))
                .collect(Collectors.toList());
    }

    @Override
    public void markTouched(Long id, ZonedDateTime touchedAt) {
        FvgZone f = store.get(id);
        if (f != null) {
            f.setStatus(FvgStatus.TOUCHED);
            f.setTouchedAt(touchedAt);
        }
    }

    @Override
    public void setLeftZoneAt(Long id, ZonedDateTime leftZoneAt) {
        FvgZone f = store.get(id);
        if (f != null) f.setLeftZoneAt(leftZoneAt);
    }

    @Override
    public void setAlertMode(Long id, AlertMode mode) {
        FvgZone f = store.get(id);
        if (f != null) f.setAlertMode(mode);
    }

    @Override
    public void resumeArmed(Long id) {
        FvgZone f = store.get(id);
        if (f != null) {
            f.setAlertMode(AlertMode.ARMED);
            f.setLeftZoneAt(null);
        }
    }

    @Override
    public void markFilled(Long id, ZonedDateTime filledAt, ZonedDateTime expiresAt) {
        FvgZone f = store.get(id);
        if (f != null) {
            f.setStatus(FvgStatus.FILLED);
            f.setFilledAt(filledAt);
            f.setExpiresAt(expiresAt);
        }
    }

    @Override
    public int consumeExpiredFilled(ZonedDateTime now) {
        int count = 0;
        for (FvgZone f : store.values()) {
            if (f.getStatus() == FvgStatus.FILLED
                    && f.getExpiresAt() != null
                    && !now.isBefore(f.getExpiresAt())) {
                f.setStatus(FvgStatus.CONSUMED);
                count++;
            }
        }
        return count;
    }

    @Override
    public List<FvgZone> findTouchedForSymbolOnTimeframes(Symbol symbol, List<Timeframe> timeframes) {
        return store.values().stream()
                .filter(f -> f.getSymbol().equals(symbol))
                .filter(f -> timeframes.contains(f.getTimeframe()))
                .filter(f -> f.getStatus() == FvgStatus.TOUCHED)
                .collect(Collectors.toList());
    }
}

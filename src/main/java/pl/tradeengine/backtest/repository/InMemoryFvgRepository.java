package pl.tradeengine.backtest.repository;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.port.FvgRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class InMemoryFvgRepository implements FvgRepository {

    private final Map<Long, FvgZone> fvgMap = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public FvgZone save(FvgZone fvg) {
        Long id = fvg.getId() != null ? fvg.getId() : idGenerator.getAndIncrement();
        FvgZone saved = new FvgZone(
                id, fvg.getSymbol(), fvg.getTimeframe(), fvg.getDirection(),
                fvg.getLowerPrice(), fvg.getUpperPrice(), fvg.getStrength(),
                fvg.getKind(), fvg.getStatus()
        );
        fvgMap.put(id, saved);
        log.debug("Saved FVG id={}, {} {} {} [{} - {}]",
                id, saved.getSymbol().code(), saved.getTimeframe(), saved.getDirection(),
                saved.getLowerPrice(), saved.getUpperPrice());
        return saved;
    }

    @Override
    public Optional<FvgZone> findById(Long id) {
        return Optional.ofNullable(fvgMap.get(id));
    }

    @Override
    public List<FvgZone> findIntersectingOpenFvgs(Symbol symbol, BigDecimal low, BigDecimal high) {
        return fvgMap.values().stream()
                .filter(fvg -> fvg.getSymbol().equals(symbol))
                .filter(fvg -> fvg.getStatus() != FvgStatus.CONSUMED)
                .filter(fvg -> fvg.getUpperPrice().compareTo(low) >= 0
                        && fvg.getLowerPrice().compareTo(high) <= 0)
                .collect(Collectors.toList());
    }

    @Override
    public void updateStatus(Long fvgId, FvgStatus newStatus) {

    }

    @Override
    public List<FvgZone> findTouched(Symbol symbol, Timeframe timeframe) {
        return List.of();
    }

    @Override
    public void markTouched(Long id, ZonedDateTime touchedAt) {
        FvgZone fvg = fvgMap.get(id);
        if (fvg != null) {
            FvgZone updated = new FvgZone(
                    fvg.getId(),
                    fvg.getSymbol(),
                    fvg.getTimeframe(),
                    fvg.getDirection(),
                    fvg.getLowerPrice(),
                    fvg.getUpperPrice(),
                    fvg.getStrength(),
                    fvg.getKind(),
                    FvgStatus.TOUCHED,         // ← Status
                    fvg.getAlertMode(),        // ← Zachowaj alert mode
                    touchedAt,                 // ← USTAW touchedAt!
                    fvg.getLeftZoneAt(),       // ← Zachowaj inne pola
                    fvg.getFilledAt(),         // ← Może już być filled
                    fvg.getExpiresAt()         // ← Zachowaj expires
            );
//            FvgZone updated = new FvgZone(
//                    fvg.getId(), fvg.getSymbol(), fvg.getTimeframe(), fvg.getDirection(),
//                    fvg.getLowerPrice(), fvg.getUpperPrice(), fvg.getStrength(),
//                    fvg.getKind(), FvgStatus.TOUCHED
//            );
            fvgMap.put(id, updated);
        }
    }

    @Override
    public void setLeftZoneAt(Long fvgId, ZonedDateTime leftZoneAt) {

    }

    @Override
    public void setAlertMode(Long fvgId, AlertMode mode) {

    }

    @Override
    public void resumeArmed(Long fvgId) {

    }

    @Override
    public void markFilled(Long id, ZonedDateTime filledAt, ZonedDateTime expiresAt) {
        FvgZone fvg = fvgMap.get(id);
        if (fvg != null) {
            FvgZone updated = new FvgZone(
                    fvg.getId(),
                    fvg.getSymbol(),
                    fvg.getTimeframe(),
                    fvg.getDirection(),
                    fvg.getLowerPrice(),
                    fvg.getUpperPrice(),
                    fvg.getStrength(),
                    fvg.getKind(),
                    FvgStatus.FILLED,          // ← Status
                    fvg.getAlertMode(),        // ← Zachowaj alert mode
                    fvg.getTouchedAt(),        // ← Zachowaj touchedAt (może już być)
                    fvg.getLeftZoneAt(),       // ← Zachowaj leftZoneAt
                    filledAt,                  // ← USTAW filledAt!
                    expiresAt                  // ← USTAW expiresAt
            );
//            FvgZone updated = new FvgZone(
//                    fvg.getId(), fvg.getSymbol(), fvg.getTimeframe(), fvg.getDirection(),
//                    fvg.getLowerPrice(), fvg.getUpperPrice(), fvg.getStrength(),
//                    fvg.getKind(), FvgStatus.FILLED
//            );
            fvgMap.put(id, updated);
        }
    }

    @Override
    public int consumeExpiredFilled(ZonedDateTime now) {
        return 0;
    }

    @Override
    public List<FvgZone> findTouchedForSymbolOnTimeframes(Symbol symbol, List<Timeframe> timeframes) {
        return List.of();
    }

    @Override
    public List<FvgZone> findActiveForSymbolAndDirectionOnHigherTf(
            Symbol symbol, Direction direction, List<FvgStatus> statuses, List<Timeframe> timeframes
    ) {
        return fvgMap.values().stream()
                .filter(fvg -> fvg.getSymbol().equals(symbol))
                .filter(fvg -> fvg.getDirection() == direction)
                .filter(fvg -> statuses.contains(fvg.getStatus()))
                .filter(fvg -> timeframes.contains(fvg.getTimeframe()))
                .collect(Collectors.toList());
    }

    // Dodaj pozostałe metody jeśli są wymagane przez interfejs...
}

package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.StoredSwingPoint;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.SwingPointRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public class SwingPointPersistenceAdapter implements SwingPointRepository {

    private final JpaSwingPointRepository jpaRepo;

    public SwingPointPersistenceAdapter(JpaSwingPointRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void save(Symbol symbol, Timeframe timeframe, String type, BigDecimal price, ZonedDateTime detectedAt) {
        SwingPointEntity entity = new SwingPointEntity(
                symbol.code(),
                timeframe,
                type,
                price,
                detectedAt
        );
        jpaRepo.save(entity);
    }

    @Override
    public List<StoredSwingPoint> findRecentSwings(Symbol symbol, Timeframe timeframe, String type, ZonedDateTime since) {
        return jpaRepo.findRecent(symbol.code(), timeframe, type, since)
                .stream()
                .map(e -> new StoredSwingPoint(
                        new Symbol(e.getSymbol()),
                        e.getTimeframe(),
                        e.getType(),
                        e.getPrice(),
                        e.getDetectedAt()
                ))
                .toList();
    }

    @Override
    public Direction getLastSwingDirection(Symbol symbol, Timeframe timeframe) {
        ZonedDateTime veryOldDate = ZonedDateTime.now().minusYears(1);

        List<StoredSwingPoint> lows = findRecentSwings(symbol, timeframe, "SWING_LOW", veryOldDate);
        List<StoredSwingPoint> highs = findRecentSwings(symbol, timeframe, "SWING_HIGH", veryOldDate);

        if (lows.isEmpty() || highs.isEmpty()) {
            return null; // Jak NEUTRAL w BiasRepository
        }

        StoredSwingPoint lastLow = lows.get(lows.size() - 1);
        StoredSwingPoint lastHigh = highs.get(highs.size() - 1);

        // Ostatni swing to LOW = bullish momentum = LONG
        // Ostatni swing to HIGH = bearish momentum = SHORT
        return lastLow.detectedAt().isAfter(lastHigh.detectedAt())
                ? Direction.LONG
                : Direction.SHORT;
    }

}

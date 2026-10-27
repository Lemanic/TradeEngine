package pl.tradeengine;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.StoredSwingPoint;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.SwingPointRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wierny fake SwingPointRepository — w dwoch miejscach, w ktorych
 * {@code backtest.repository.InMemorySwingPointRepository} rozjezdza sie z produkcja:
 *
 * <ol>
 *   <li><b>getLastSwingDirection</b> — InMemory zwraca na twardo {@code null} (linia 38), a
 *       {@code SwingPointPersistenceAdapter} ma trzy galezie. GrinderStrategyScenario czyta te
 *       metode jako bramke kierunku (linie 93 i 192), wiec na tamtym fake'u bramka jest zawsze
 *       przepustowa i galezie FVG Grindera nie daja sie przetestowac.</li>
 *   <li><b>findRecentSwings</b> — InMemory filtruje {@code isAfter(since)} (ekskluzywnie), a JPQL
 *       {@code findRecent} ma {@code detectedAt >= :since} (inkluzywnie). Tutaj jest inkluzywnie,
 *       czyli tak jak w produkcji.</li>
 * </ol>
 *
 * Zwraca posortowane rosnaco po detectedAt, bo na tym opiera sie {@code get(size()-1)} zarowno
 * w adapterze, jak i w scenariuszach — JPQL gwarantuje to przez ORDER BY, lista w pamieci nie.
 */
public class StatefulSwingPointRepositoryFake implements SwingPointRepository {

    private final List<StoredSwingPoint> swings = new ArrayList<>();

    @Override
    public void save(Symbol symbol, Timeframe timeframe, String type, BigDecimal price, ZonedDateTime detectedAt) {
        swings.add(new StoredSwingPoint(symbol, timeframe, type, price, detectedAt));
    }

    @Override
    public List<StoredSwingPoint> findRecentSwings(Symbol symbol, Timeframe timeframe, String type, ZonedDateTime since) {
        return swings.stream()
                .filter(s -> s.symbol().equals(symbol))
                .filter(s -> s.timeframe() == timeframe)
                .filter(s -> s.type().equals(type))
                .filter(s -> !s.detectedAt().isBefore(since))
                .sorted(Comparator.comparing(StoredSwingPoint::detectedAt))
                .toList();
    }

    /** Odwzorowuje SwingPointPersistenceAdapter.getLastSwingDirection, w tym remis -> SHORT. */
    @Override
    public Direction getLastSwingDirection(Symbol symbol, Timeframe timeframe) {
        ZonedDateTime veryOldDate = ZonedDateTime.now().minusYears(1);

        List<StoredSwingPoint> lows = findRecentSwings(symbol, timeframe, "SWING_LOW", veryOldDate);
        List<StoredSwingPoint> highs = findRecentSwings(symbol, timeframe, "SWING_HIGH", veryOldDate);

        if (lows.isEmpty() || highs.isEmpty()) {
            return null;
        }

        StoredSwingPoint lastLow = lows.get(lows.size() - 1);
        StoredSwingPoint lastHigh = highs.get(highs.size() - 1);

        return lastLow.detectedAt().isAfter(lastHigh.detectedAt())
                ? Direction.LONG
                : Direction.SHORT;
    }

    /** Wszystko, co zapisano, w kolejnosci zapisu — do asercji na tym, co serwis przekazal. */
    public List<StoredSwingPoint> saved() {
        return List.copyOf(swings);
    }

    public void clear() {
        swings.clear();
    }
}

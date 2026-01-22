package pl.tradeengine.backtest.repository;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.StoredSwingPoint;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.SwingPointRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class InMemorySwingPointRepository implements SwingPointRepository {

    private final List<StoredSwingPoint> swings = new ArrayList<>();

    @Override
    public void save(Symbol symbol, Timeframe tf, String type, BigDecimal price, ZonedDateTime time) {
        StoredSwingPoint swing = new StoredSwingPoint(symbol, tf, type, price, time);
        swings.add(swing);
        log.debug("Saved swing: {} {} {} at {} (price: {})", symbol.code(), tf, type, time, price);
    }

    @Override
    public List<StoredSwingPoint> findRecentSwings(Symbol symbol, Timeframe tf, String type, ZonedDateTime since) {
        return swings.stream()
                .filter(s -> s.symbol().equals(symbol))
                .filter(s -> s.timeframe().equals(tf))
                .filter(s -> s.type().equals(type))
                .filter(s -> s.detectedAt().isAfter(since))
                .toList();
    }
}

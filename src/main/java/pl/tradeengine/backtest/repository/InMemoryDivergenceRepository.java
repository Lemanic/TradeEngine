package pl.tradeengine.backtest.repository;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.DivergenceRepository;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class InMemoryDivergenceRepository implements DivergenceRepository {

    private final Map<Long, DivergenceSignal> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public DivergenceSignal save(DivergenceSignal s) {
        Long id = s.getId() != null ? s.getId() : idGenerator.getAndIncrement();
        DivergenceSignal saved = new DivergenceSignal(
                id, s.getSymbol(), s.getTimeframe(), s.getDirection(), s.getStrength(), s.getDetectedAt()
        );
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<DivergenceSignal> findMostRecent(Symbol symbol, Timeframe timeframe, ZonedDateTime since) {
        return store.values().stream()
                .filter(s -> s.getSymbol().equals(symbol))
                .filter(s -> s.getTimeframe() == timeframe)
                .filter(s -> !s.getDetectedAt().isBefore(since))
                .max(Comparator.comparing(DivergenceSignal::getDetectedAt));
    }

    @Override
    public Optional<DivergenceSignal> findMostRecentByDirection(Symbol symbol, Timeframe timeframe,
                                                                Direction direction, ZonedDateTime since) {
        return store.values().stream()
                .filter(s -> s.getSymbol().equals(symbol))
                .filter(s -> s.getTimeframe() == timeframe)
                .filter(s -> s.getDirection() == direction)
                .filter(s -> !s.getDetectedAt().isBefore(since))
                .max(Comparator.comparing(DivergenceSignal::getDetectedAt));
    }

    @Override
    public List<DivergenceSignal> findAllByDirectionSince(Symbol symbol, Timeframe timeframe,
                                                          Direction direction, ZonedDateTime since) {
        return store.values().stream()
                .filter(s -> s.getSymbol().equals(symbol))
                .filter(s -> s.getTimeframe() == timeframe)
                .filter(s -> s.getDirection() == direction)
                .filter(s -> !s.getDetectedAt().isBefore(since))
                .collect(Collectors.toList());
    }

    @Override
    public List<DivergenceSignal> findAll() {
        return List.copyOf(store.values());
    }
}

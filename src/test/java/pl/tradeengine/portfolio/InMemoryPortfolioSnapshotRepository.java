package pl.tradeengine.portfolio;

import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;
import pl.tradeengine.domain.port.portfolio.PortfolioSnapshotRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fake standing in for the JPA adapter in service tests — keyed by {@code takenAt}, so storing
 * the same hour twice replaces the entry exactly as the real adapter does.
 */
public class InMemoryPortfolioSnapshotRepository implements PortfolioSnapshotRepository {

    private final NavigableMap<Instant, PortfolioSnapshot> store = new TreeMap<>();
    private long nextId = 1;

    @Override
    public PortfolioSnapshot save(PortfolioSnapshot snapshot) {
        long snapshotId = nextId++;
        List<PortfolioPosition> positions = new ArrayList<>();
        for (PortfolioPosition position : snapshot.positions()) {
            positions.add(new PortfolioPosition(
                    nextId++,
                    position.source(), position.network(), position.asset(), position.cmcId(),
                    position.amount(), position.priceUsd(), position.pricePln(),
                    position.valueUsd(), position.valuePln(), position.stale()));
        }

        PortfolioSnapshot stored = new PortfolioSnapshot(
                snapshotId, snapshot.takenAt(), snapshot.totalUsd(), snapshot.totalPln(),
                snapshot.complete(), List.copyOf(positions));
        store.put(snapshot.takenAt(), stored);
        return stored;
    }

    @Override
    public Optional<PortfolioSnapshot> findLatest() {
        return Optional.ofNullable(store.isEmpty() ? null : store.lastEntry().getValue());
    }

    @Override
    public Optional<SnapshotHeader> findLatestHeader() {
        return findLatest().map(PortfolioSnapshot::header);
    }

    @Override
    public Optional<SnapshotHeader> findLastHeaderNotAfter(Instant takenAt) {
        return Optional.ofNullable(store.floorEntry(takenAt))
                .map(entry -> entry.getValue().header());
    }

    @Override
    public Optional<SnapshotHeader> findAth(FiatCurrency currency) {
        return store.values().stream()
                .max(Comparator.comparing(snapshot -> snapshot.total(currency)))
                .map(PortfolioSnapshot::header);
    }

    @Override
    public List<SnapshotHeader> findHeadersAfter(Instant exclusiveFrom, Instant inclusiveTo) {
        return store.subMap(exclusiveFrom, false, inclusiveTo, true).values().stream()
                .map(PortfolioSnapshot::header)
                .toList();
    }

    @Override
    public Optional<Instant> findFirstTakenAt() {
        return Optional.ofNullable(store.isEmpty() ? null : store.firstKey());
    }

    @Override
    public List<PortfolioPosition> findPositionsOfSourceAt(String source, Set<String> networks,
                                                           Instant takenAt) {
        PortfolioSnapshot snapshot = store.get(takenAt);
        if (snapshot == null) {
            return List.of();
        }
        return snapshot.positions().stream()
                .filter(position -> position.source().equals(source)
                        && networks.contains(position.network()))
                .toList();
    }

    @Override
    public Optional<BigDecimal> findLastKnownPrice(int cmcId, FiatCurrency currency) {
        return store.descendingMap().values().stream()
                .flatMap(snapshot -> snapshot.positions().stream())
                .filter(position -> position.cmcId() == cmcId)
                .map(position -> position.price(currency))
                .findFirst();
    }

    /** Everything stored, oldest first. */
    public Collection<PortfolioSnapshot> all() {
        return store.values();
    }

    /** Empties the fake — for tests that share one instance across cases. */
    public void clear() {
        store.clear();
    }
}

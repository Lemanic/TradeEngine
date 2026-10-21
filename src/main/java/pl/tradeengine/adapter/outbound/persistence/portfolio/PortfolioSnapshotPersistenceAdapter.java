package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;
import pl.tradeengine.domain.port.portfolio.PortfolioSnapshotRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class PortfolioSnapshotPersistenceAdapter implements PortfolioSnapshotRepository {

    private static final Comparator<PortfolioPosition> STABLE_ORDER = Comparator
            .comparing(PortfolioPosition::source)
            .thenComparing(PortfolioPosition::network)
            .thenComparing(PortfolioPosition::asset);

    private final JpaPortfolioSnapshotRepository snapshots;
    private final JpaPortfolioPositionRepository positions;

    public PortfolioSnapshotPersistenceAdapter(JpaPortfolioSnapshotRepository snapshots,
                                               JpaPortfolioPositionRepository positions) {
        this.snapshots = snapshots;
        this.positions = positions;
    }

    @Override
    @Transactional
    public PortfolioSnapshot save(PortfolioSnapshot snapshot) {
        OffsetDateTime takenAt = toOffset(snapshot.takenAt());

        // One hour, one snapshot. The flush is load-bearing: without it Hibernate orders the
        // insert before the delete on commit and trips the unique constraint on taken_at.
        snapshots.deleteByTakenAt(takenAt);
        snapshots.flush();

        PortfolioSnapshotEntity entity = new PortfolioSnapshotEntity();
        entity.setTakenAt(takenAt);
        entity.setTotalUsd(snapshot.totalUsd());
        entity.setTotalPln(snapshot.totalPln());
        entity.setComplete(snapshot.complete());
        snapshot.positions().forEach(position -> entity.addPosition(toEntity(position)));

        return toDomain(snapshots.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PortfolioSnapshot> findLatest() {
        return snapshots.findLatestWithPositions().stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<SnapshotHeader> findLatestHeader() {
        return snapshots.findFirstByOrderByTakenAtDesc().map(PortfolioSnapshotPersistenceAdapter::toHeader);
    }

    @Override
    public Optional<SnapshotHeader> findLastHeaderNotAfter(Instant takenAt) {
        return snapshots.findFirstByTakenAtLessThanEqualOrderByTakenAtDesc(toOffset(takenAt))
                .map(PortfolioSnapshotPersistenceAdapter::toHeader);
    }

    @Override
    public Optional<SnapshotHeader> findAth(FiatCurrency currency) {
        Optional<PortfolioSnapshotEntity> peak = currency == FiatCurrency.USD
                ? snapshots.findFirstByOrderByTotalUsdDesc()
                : snapshots.findFirstByOrderByTotalPlnDesc();
        return peak.map(PortfolioSnapshotPersistenceAdapter::toHeader);
    }

    @Override
    public List<SnapshotHeader> findHeadersAfter(Instant exclusiveFrom, Instant inclusiveTo) {
        return snapshots.findByTakenAtGreaterThanAndTakenAtLessThanEqualOrderByTakenAtAsc(
                        toOffset(exclusiveFrom), toOffset(inclusiveTo))
                .stream()
                .map(PortfolioSnapshotPersistenceAdapter::toHeader)
                .toList();
    }

    @Override
    public Optional<Instant> findFirstTakenAt() {
        return snapshots.findFirstByOrderByTakenAtAsc()
                .map(entity -> entity.getTakenAt().toInstant());
    }

    @Override
    public List<PortfolioPosition> findPositionsOfSourceAt(String source, Set<String> networks,
                                                           Instant takenAt) {
        if (networks.isEmpty()) {
            return List.of();
        }
        return positions
                .findPositionsOfSourceAt(source, List.copyOf(networks), toOffset(takenAt))
                .stream()
                .map(PortfolioSnapshotPersistenceAdapter::toDomain)
                .sorted(STABLE_ORDER)
                .toList();
    }

    @Override
    public Optional<BigDecimal> findLastKnownPrice(int cmcId, FiatCurrency currency) {
        PageRequest onlyNewest = PageRequest.of(0, 1);
        List<BigDecimal> prices = currency == FiatCurrency.USD
                ? positions.findRecentUsdPrices(cmcId, onlyNewest)
                : positions.findRecentPlnPrices(cmcId, onlyNewest);
        return prices.stream().findFirst();
    }

    private PortfolioPositionEntity toEntity(PortfolioPosition position) {
        PortfolioPositionEntity entity = new PortfolioPositionEntity();
        entity.setSource(position.source());
        entity.setNetwork(position.network());
        entity.setAsset(position.asset());
        entity.setCmcId(position.cmcId());
        entity.setAmount(position.amount());
        entity.setPriceUsd(position.priceUsd());
        entity.setPricePln(position.pricePln());
        entity.setValueUsd(position.valueUsd());
        entity.setValuePln(position.valuePln());
        entity.setStale(position.stale());
        return entity;
    }

    private PortfolioSnapshot toDomain(PortfolioSnapshotEntity entity) {
        List<PortfolioPosition> mapped = entity.getPositions().stream()
                .map(PortfolioSnapshotPersistenceAdapter::toDomain)
                .sorted(STABLE_ORDER)
                .toList();

        return new PortfolioSnapshot(
                entity.getId(),
                entity.getTakenAt().toInstant(),
                entity.getTotalUsd(),
                entity.getTotalPln(),
                entity.isComplete(),
                mapped
        );
    }

    private static PortfolioPosition toDomain(PortfolioPositionEntity entity) {
        return new PortfolioPosition(
                entity.getId(),
                entity.getSource(),
                entity.getNetwork(),
                entity.getAsset(),
                entity.getCmcId(),
                entity.getAmount(),
                entity.getPriceUsd(),
                entity.getPricePln(),
                entity.getValueUsd(),
                entity.getValuePln(),
                entity.isStale()
        );
    }

    private static SnapshotHeader toHeader(PortfolioSnapshotEntity entity) {
        return new SnapshotHeader(
                entity.getTakenAt().toInstant(),
                entity.getTotalUsd(),
                entity.getTotalPln(),
                entity.isComplete()
        );
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

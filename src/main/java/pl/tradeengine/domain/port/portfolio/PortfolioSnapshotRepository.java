package pl.tradeengine.domain.port.portfolio;

import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;

import pl.tradeengine.domain.model.portfolio.PortfolioPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PortfolioSnapshotRepository {

    /** Saves the snapshot, replacing any snapshot already stored for the same {@code takenAt}. */
    PortfolioSnapshot save(PortfolioSnapshot snapshot);

    /** The newest snapshot together with its positions. */
    Optional<PortfolioSnapshot> findLatest();

    /** The newest snapshot header, without positions. */
    Optional<SnapshotHeader> findLatestHeader();

    /** The newest snapshot header not later than {@code takenAt}. Used for 24h/7d/30d change. */
    Optional<SnapshotHeader> findLastHeaderNotAfter(Instant takenAt);

    /** The snapshot with the highest total in the given currency. */
    Optional<SnapshotHeader> findAth(FiatCurrency currency);

    /**
     * Headers in {@code (exclusiveFrom, inclusiveTo]}, ascending by {@code takenAt}.
     *
     * <p>The lower bound is exclusive on purpose: with hourly snapshots a 24h window then holds
     * at most 24 points, which is the maximum the API contract documents. Closing both ends would
     * yield 25.
     */
    List<SnapshotHeader> findHeadersAfter(Instant exclusiveFrom, Instant inclusiveTo);

    /** {@code takenAt} of the oldest snapshot. Used as the start of the {@code ALL} range. */
    Optional<Instant> findFirstTakenAt();

    /**
     * Positions that one source contributed to the snapshot taken at {@code takenAt}.
     *
     * <p>A source owns exactly the positions carrying its label on one of its networks, which is
     * what lets a failing source be told apart from its siblings under the same label.
     */
    List<PortfolioPosition> findPositionsOfSourceAt(String source, Set<String> networks,
                                                    Instant takenAt);

    /** The most recently stored price for an asset, used when the price provider skips it. */
    Optional<BigDecimal> findLastKnownPrice(int cmcId, FiatCurrency currency);
}

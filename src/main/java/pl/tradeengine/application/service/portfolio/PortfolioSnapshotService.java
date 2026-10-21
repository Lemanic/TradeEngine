package pl.tradeengine.application.service.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.portfolio.AssetPrice;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;
import pl.tradeengine.domain.port.portfolio.PortfolioSnapshotRepository;
import pl.tradeengine.domain.port.portfolio.PriceProvider;
import pl.tradeengine.domain.port.portfolio.PriceUnavailableException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds one snapshot: read amounts from every source, price them, store the result.
 *
 * <p>Degradation rules, in the order they bite:
 * <ol>
 *   <li>a source throws → its amounts come from the previous snapshot, those positions are
 *       {@code stale} and the snapshot is {@code complete=false};</li>
 *   <li>an asset has no fresh price → the last price stored for it is reused and the position
 *       is {@code stale}; with no price history at all the position is dropped and the snapshot
 *       is {@code complete=false} — a zero would lie about the portfolio's value;</li>
 *   <li>prices cannot be fetched at all → nothing is written, only an error is logged.</li>
 * </ol>
 */
public class PortfolioSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotService.class);

    /** Money is stored with sub-cent precision; the API rounds to 2 places on the way out. */
    private static final int MONEY_SCALE = 8;

    private static final Comparator<PortfolioPosition> STABLE_ORDER = Comparator
            .comparing(PortfolioPosition::source)
            .thenComparing(PortfolioPosition::network)
            .thenComparing(PortfolioPosition::asset);

    private final List<HoldingsSource> sources;
    private final PriceProvider priceProvider;
    private final PortfolioSnapshotRepository repository;
    private final Clock clock;
    private final Duration carryForwardMaxAge;

    public PortfolioSnapshotService(List<HoldingsSource> sources,
                                    PriceProvider priceProvider,
                                    PortfolioSnapshotRepository repository,
                                    Clock clock,
                                    Duration carryForwardMaxAge) {
        this.sources = List.copyOf(sources);
        this.priceProvider = priceProvider;
        this.repository = repository;
        this.clock = clock;
        this.carryForwardMaxAge = carryForwardMaxAge;
    }

    /**
     * @return the stored snapshot, or empty when nothing was written (no sources configured, or
     *         prices unavailable).
     */
    public Optional<PortfolioSnapshot> takeSnapshot() {
        Instant takenAt = clock.instant().truncatedTo(ChronoUnit.HOURS);

        if (sources.isEmpty()) {
            log.warn("No holdings source is configured — skipping the {} snapshot. "
                    + "Are the PORTFOLIO_* wallet addresses set?", takenAt);
            return Optional.empty();
        }

        List<HoldingsSource> failed = new ArrayList<>();
        List<Holding> fresh = new ArrayList<>();
        for (HoldingsSource source : sources) {
            try {
                fresh.addAll(source.fetchHoldings());
            } catch (RuntimeException e) {
                log.error("Holdings source '{}' {} failed, falling back to its last known amounts: {}",
                        source.name(), source.networks(), e.toString());
                failed.add(source);
            }
        }

        List<Candidate> candidates = new ArrayList<>(aggregate(fresh));
        if (!failed.isEmpty()) {
            candidates.addAll(carryForward(failed, takenAt));
        }

        Set<Integer> cmcIds = candidates.stream()
                .map(candidate -> candidate.holding().cmcId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Integer, AssetPrice> prices;
        try {
            prices = priceProvider.pricesFor(cmcIds);
        } catch (PriceUnavailableException e) {
            log.error("Prices unavailable — no snapshot written for {}: {}", takenAt, e.getMessage());
            return Optional.empty();
        }

        boolean complete = failed.isEmpty();
        List<PortfolioPosition> positions = new ArrayList<>();
        BigDecimal totalUsd = BigDecimal.ZERO;
        BigDecimal totalPln = BigDecimal.ZERO;

        for (Candidate candidate : candidates) {
            Holding holding = candidate.holding();
            AssetPrice price = prices.get(holding.cmcId());
            boolean priceCarried = false;

            if (price == null) {
                Optional<AssetPrice> lastKnown = lastKnownPrice(holding.cmcId());
                if (lastKnown.isEmpty()) {
                    log.warn("No price and no price history for {} (cmcId {}) — position dropped, "
                            + "snapshot marked incomplete", holding.asset(), holding.cmcId());
                    complete = false;
                    continue;
                }
                price = lastKnown.get();
                priceCarried = true;
                log.warn("No fresh price for {} (cmcId {}) — using the last known one ({} USD)",
                        holding.asset(), holding.cmcId(), price.usd());
            }

            BigDecimal valueUsd = money(holding.amount().multiply(price.usd()));
            BigDecimal valuePln = money(holding.amount().multiply(price.pln()));

            positions.add(new PortfolioPosition(
                    null,
                    holding.source(),
                    holding.network(),
                    holding.asset(),
                    holding.cmcId(),
                    holding.amount(),
                    price.usd(),
                    price.pln(),
                    valueUsd,
                    valuePln,
                    candidate.amountCarried() || priceCarried
            ));

            totalUsd = totalUsd.add(valueUsd);
            totalPln = totalPln.add(valuePln);
        }

        positions.sort(STABLE_ORDER);

        PortfolioSnapshot saved = repository.save(new PortfolioSnapshot(
                null, takenAt, money(totalUsd), money(totalPln), complete, List.copyOf(positions)));

        log.info("Snapshot {} stored: {} position(s), {} USD / {} PLN, complete={}",
                takenAt, positions.size(), saved.totalUsd(), saved.totalPln(), complete);
        return Optional.of(saved);
    }

    /** Sums holdings that land on the same (source, network, asset) — e.g. two Rabby addresses. */
    private static List<Candidate> aggregate(List<Holding> holdings) {
        Map<PositionKey, Holding> merged = new LinkedHashMap<>();
        for (Holding holding : holdings) {
            merged.merge(PositionKey.of(holding), holding, (left, right) -> new Holding(
                    left.source(), left.network(), left.asset(), left.cmcId(),
                    left.amount().add(right.amount())));
        }
        return merged.values().stream()
                .map(holding -> new Candidate(holding, false))
                .toList();
    }

    /**
     * Amounts of the failed sources, taken from the newest stored snapshot. A source owns exactly
     * the positions carrying its label on one of its networks, so a label split across two sources
     * (EVM + xpub) only loses the half that actually failed.
     */
    /**
     * Amounts of the failed sources, so a source that cannot be reached leaves no hole in the
     * total and no step in the chart.
     *
     * <p>Walks snapshots backwards and stops on the first conclusive answer:
     * <ul>
     *   <li>the snapshot <b>has</b> positions for this source → carry those amounts, priced at
     *       today's rates;</li>
     *   <li>the snapshot has none but is {@code complete} → every source answered in it, so the
     *       absence is the source itself saying "nothing here any more". Carrying an older amount
     *       would resurrect something that was sold, so nothing is carried;</li>
     *   <li>the snapshot has none and is {@code complete=false} → inconclusive, because the
     *       source may have been failing then too. Keep looking further back.</li>
     * </ul>
     *
     * <p>The walk is bounded by {@code carryForwardMaxAge}: an amount stale by a week may well
     * have been spent, and a wrong number is worse than an honest gap.
     */
    private List<Candidate> carryForward(List<HoldingsSource> failed, Instant takenAt) {
        Instant notOlderThan = takenAt.minus(carryForwardMaxAge);
        List<SnapshotHeader> window = new ArrayList<>(
                repository.findHeadersAfter(notOlderThan, takenAt));
        Collections.reverse(window);   // najnowszy pierwszy

        List<Candidate> carried = new ArrayList<>();
        for (HoldingsSource source : failed) {
            carried.addAll(carryForwardOne(source, window, notOlderThan));
        }
        return carried;
    }

    private List<Candidate> carryForwardOne(HoldingsSource source, List<SnapshotHeader> newestFirst,
                                            Instant notOlderThan) {
        for (SnapshotHeader header : newestFirst) {
            List<PortfolioPosition> found = repository.findPositionsOfSourceAt(
                    source.name(), source.networks(), header.takenAt());

            if (!found.isEmpty()) {
                log.warn("Carried {} position(s) forward for '{}' {} from the snapshot of {} — "
                                + "amounts are that old, prices are current",
                        found.size(), source.name(), source.networks(), header.takenAt());
                return found.stream()
                        .map(position -> new Candidate(new Holding(
                                position.source(), position.network(), position.asset(),
                                position.cmcId(), position.amount()), true))
                        .toList();
            }
            if (header.complete()) {
                log.info("'{}' {} reported nothing in the complete snapshot of {} — treating that "
                                + "as emptied rather than carrying an older amount",
                        source.name(), source.networks(), header.takenAt());
                return List.of();
            }
        }

        log.warn("Nothing to carry forward for '{}' {} — no position stored since {}. "
                        + "Its value is missing from this snapshot.",
                source.name(), source.networks(), notOlderThan);
        return List.of();
    }

    private Optional<AssetPrice> lastKnownPrice(int cmcId) {
        Optional<BigDecimal> usd = repository.findLastKnownPrice(cmcId, FiatCurrency.USD);
        Optional<BigDecimal> pln = repository.findLastKnownPrice(cmcId, FiatCurrency.PLN);
        return usd.isPresent() && pln.isPresent()
                ? Optional.of(new AssetPrice(usd.get(), pln.get()))
                : Optional.empty();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** A holding on its way into the snapshot, plus whether its amount is a carried-over one. */
    private record Candidate(Holding holding, boolean amountCarried) {}

    private record PositionKey(String source, String network, String asset, int cmcId) {
        static PositionKey of(Holding holding) {
            return new PositionKey(holding.source(), holding.network(), holding.asset(), holding.cmcId());
        }
    }
}

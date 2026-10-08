package pl.tradeengine.application.service.portfolio;

import pl.tradeengine.application.dto.portfolio.AthDto;
import pl.tradeengine.application.dto.portfolio.ChangeDto;
import pl.tradeengine.application.dto.portfolio.ChangeSetDto;
import pl.tradeengine.application.dto.portfolio.HistoryPointDto;
import pl.tradeengine.application.dto.portfolio.PortfolioHistoryResponse;
import pl.tradeengine.application.dto.portfolio.PortfolioSummaryResponse;
import pl.tradeengine.application.dto.portfolio.PositionDto;
import pl.tradeengine.application.dto.portfolio.SourceDto;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.HistoryRange;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;
import pl.tradeengine.domain.port.portfolio.PortfolioSnapshotRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static pl.tradeengine.application.service.portfolio.PortfolioMetrics.amount;
import static pl.tradeengine.application.service.portfolio.PortfolioMetrics.money;
import static pl.tradeengine.application.service.portfolio.PortfolioMetrics.pct;
import static pl.tradeengine.application.service.portfolio.PortfolioMetrics.price;

/**
 * Read side of the portfolio: everything the dashboard asks for, derived from stored snapshots.
 *
 * <p>Both endpoints are anchored to the <em>newest stored snapshot</em>, not to the wall clock.
 * That is what keeps the contract's consistency rule true — the last history point is always the
 * same snapshot that {@code summary} describes, even if collection stalled hours ago.
 */
public class PortfolioQueryService {

    private static final Duration H24 = Duration.ofHours(24);
    private static final Duration D7 = Duration.ofDays(7);
    private static final Duration D30 = Duration.ofDays(30);

    private final PortfolioSnapshotRepository repository;

    public PortfolioQueryService(PortfolioSnapshotRepository repository) {
        this.repository = repository;
    }

    /** Empty when no snapshot exists at all — the controller turns that into 204. */
    public Optional<PortfolioSummaryResponse> summary(FiatCurrency currency) {
        return repository.findLatest().map(snapshot -> toSummary(snapshot, currency));
    }

    public PortfolioHistoryResponse history(HistoryRange range, FiatCurrency currency) {
        Optional<SnapshotHeader> latest = repository.findLatestHeader();
        if (latest.isEmpty()) {
            // 200 with no points, not 204: an empty series is a natural answer here.
            return new PortfolioHistoryResponse(range, range.interval(), currency, List.of());
        }

        Instant to = latest.get().takenAt();
        // Exclusive lower bound keeps a 24h window at 24 hourly points, not 25. ALL has no
        // look-back, so it starts at the epoch and keeps the very first snapshot.
        Instant exclusiveFrom = range.lookback().map(to::minus).orElse(Instant.EPOCH);

        List<HistoryPointDto> points = PortfolioMetrics
                .sample(repository.findHeadersAfter(exclusiveFrom, to), range.interval())
                .stream()
                .map(header -> new HistoryPointDto(
                        header.takenAt(), money(header.total(currency)), header.complete()))
                .toList();

        return new PortfolioHistoryResponse(range, range.interval(), currency, points);
    }

    private PortfolioSummaryResponse toSummary(PortfolioSnapshot snapshot, FiatCurrency currency) {
        BigDecimal total = snapshot.total(currency);

        return new PortfolioSummaryResponse(
                snapshot.takenAt(),
                currency,
                money(total),
                snapshot.complete(),
                new ChangeSetDto(
                        change(snapshot, currency, H24),
                        change(snapshot, currency, D7),
                        change(snapshot, currency, D30)),
                ath(currency),
                sources(snapshot, currency, total),
                positions(snapshot, currency, total)
        );
    }

    /**
     * Compares against the newest snapshot no later than {@code takenAt − period}.
     *
     * @return {@code null} when there is no such snapshot, or when its total was zero and the
     *         relative change would be undefined.
     */
    private ChangeDto change(PortfolioSnapshot snapshot, FiatCurrency currency, Duration period) {
        return repository.findLastHeaderNotAfter(snapshot.takenAt().minus(period))
                .map(previous -> {
                    BigDecimal before = previous.total(currency);
                    if (before.signum() == 0) {
                        return null;
                    }
                    BigDecimal difference = snapshot.total(currency).subtract(before);
                    return new ChangeDto(money(difference), pct(difference, before));
                })
                .orElse(null);
    }

    private AthDto ath(FiatCurrency currency) {
        return repository.findAth(currency)
                .map(peak -> new AthDto(money(peak.total(currency)), peak.takenAt()))
                .orElse(null);
    }

    /** Positions summed per source label; shares use unrounded values and round at the end. */
    private static List<SourceDto> sources(PortfolioSnapshot snapshot, FiatCurrency currency,
                                           BigDecimal total) {
        Map<String, BigDecimal> perSource = new LinkedHashMap<>();
        for (PortfolioPosition position : snapshot.positions()) {
            perSource.merge(position.source(), position.value(currency), BigDecimal::add);
        }
        return perSource.entrySet().stream()
                .map(entry -> new SourceDto(
                        entry.getKey(), money(entry.getValue()), pct(entry.getValue(), total)))
                .toList();
    }

    private static List<PositionDto> positions(PortfolioSnapshot snapshot, FiatCurrency currency,
                                               BigDecimal total) {
        return snapshot.positions().stream()
                .map(position -> new PositionDto(
                        position.source(),
                        position.network(),
                        position.asset(),
                        amount(position.amount()),
                        price(position.price(currency)),
                        money(position.value(currency)),
                        pct(position.value(currency), total),
                        position.stale()))
                .toList();
    }
}

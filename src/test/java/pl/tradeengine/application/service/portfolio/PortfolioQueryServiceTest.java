package pl.tradeengine.application.service.portfolio;

import org.junit.jupiter.api.Test;
import pl.tradeengine.application.dto.portfolio.PortfolioHistoryResponse;
import pl.tradeengine.application.dto.portfolio.PortfolioSummaryResponse;
import pl.tradeengine.application.dto.portfolio.PositionDto;
import pl.tradeengine.application.dto.portfolio.SourceDto;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.HistoryInterval;
import pl.tradeengine.domain.model.portfolio.HistoryRange;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.portfolio.InMemoryPortfolioSnapshotRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PortfolioQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T07:00:00Z");

    private final InMemoryPortfolioSnapshotRepository repository = new InMemoryPortfolioSnapshotRepository();
    private final PortfolioQueryService service = new PortfolioQueryService(repository);

    private static PortfolioPosition position(String source, String network, String asset,
                                              String amount, String priceUsd, boolean stale) {
        BigDecimal units = new BigDecimal(amount);
        BigDecimal usd = new BigDecimal(priceUsd);
        BigDecimal pln = usd.multiply(new BigDecimal("3.7"));
        return new PortfolioPosition(null, source, network, asset, 1, units, usd, pln,
                units.multiply(usd), units.multiply(pln), stale);
    }

    private PortfolioSnapshot store(Instant takenAt, boolean complete, PortfolioPosition... positions) {
        BigDecimal totalUsd = List.of(positions).stream()
                .map(PortfolioPosition::valueUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPln = List.of(positions).stream()
                .map(PortfolioPosition::valuePln).reduce(BigDecimal.ZERO, BigDecimal::add);
        return repository.save(new PortfolioSnapshot(null, takenAt, totalUsd, totalPln,
                complete, List.of(positions)));
    }

    private void storeTotals(Instant takenAt, String totalUsd, String totalPln) {
        repository.save(new PortfolioSnapshot(null, takenAt, new BigDecimal(totalUsd),
                new BigDecimal(totalPln), true, List.of()));
    }

    @Test
    void summaryIsEmptyWithoutAnySnapshot() {
        assertThat(service.summary(FiatCurrency.USD)).isEmpty();
    }

    @Test
    void summaryAggregatesSourcesAndSharesFromTheLatestSnapshot() {
        store(NOW, true,
                position("Rabby", "ethereum", "ETH", "2", "3000", false),
                position("Rabby", "arbitrum", "ETH", "1", "3000", false),
                position("Trezor", "bsc", "BNB", "10", "300", false));

        PortfolioSummaryResponse summary = service.summary(FiatCurrency.USD).orElseThrow();

        assertThat(summary.takenAt()).isEqualTo(NOW);
        assertThat(summary.currency()).isEqualTo(FiatCurrency.USD);
        assertThat(summary.total()).isEqualByComparingTo("12000.00");
        assertThat(summary.complete()).isTrue();
        assertThat(summary.sources())
                .extracting(SourceDto::name, source -> source.value().toPlainString(),
                        source -> source.pct().toPlainString())
                .containsExactly(
                        tuple("Rabby", "9000.00", "75.0000"),
                        tuple("Trezor", "3000.00", "25.0000"));
        assertThat(summary.positions()).hasSize(3)
                .extracting(PositionDto::asset, position -> position.pct().toPlainString())
                .contains(tuple("BNB", "25.0000"));
    }

    @Test
    void summarySharesAreTheSameInBothCurrenciesButTotalsAreNot() {
        store(NOW, true, position("Rabby", "ethereum", "ETH", "2", "3000", false));

        PortfolioSummaryResponse usd = service.summary(FiatCurrency.USD).orElseThrow();
        PortfolioSummaryResponse pln = service.summary(FiatCurrency.PLN).orElseThrow();

        assertThat(usd.total()).isEqualByComparingTo("6000.00");
        assertThat(pln.total()).isEqualByComparingTo("22200.00");
        assertThat(pln.positions().getFirst().pct()).isEqualByComparingTo(usd.positions().getFirst().pct());
    }

    @Test
    void changeIsNullUntilThereIsAnOlderSnapshot() {
        store(NOW, true, position("Rabby", "ethereum", "ETH", "2", "3000", false));

        PortfolioSummaryResponse summary = service.summary(FiatCurrency.USD).orElseThrow();

        assertThat(summary.change().h24()).isNull();
        assertThat(summary.change().d7()).isNull();
        assertThat(summary.change().d30()).isNull();
    }

    @Test
    void changeComparesAgainstTheNewestSnapshotNotLaterThanTheCutoff() {
        storeTotals(NOW.minusSeconds(31L * 24 * 3600), "8000", "29600");   // starszy niz 30 dni
        storeTotals(NOW.minusSeconds(30L * 24 * 3600), "10000", "37000");  // dokladnie 30 dni
        storeTotals(NOW.minusSeconds(7L * 24 * 3600), "11000", "40700");
        storeTotals(NOW.minusSeconds(24 * 3600), "12000", "44400");
        storeTotals(NOW, "13200", "48840");

        PortfolioSummaryResponse summary = service.summary(FiatCurrency.USD).orElseThrow();

        assertThat(summary.change().h24().abs()).isEqualByComparingTo("1200.00");
        assertThat(summary.change().h24().pct()).isEqualByComparingTo("10.0000");
        assertThat(summary.change().d7().abs()).isEqualByComparingTo("2200.00");
        assertThat(summary.change().d7().pct()).isEqualByComparingTo("20.0000");
        assertThat(summary.change().d30().abs()).isEqualByComparingTo("3200.00");
        assertThat(summary.change().d30().pct()).isEqualByComparingTo("32.0000");
    }

    @Test
    void changePercentDiffersBetweenCurrenciesWhenTheFxRateMoved() {
        storeTotals(NOW.minusSeconds(24 * 3600), "10000", "37000");  // kurs 3.70
        storeTotals(NOW, "10100", "38380");                          // kurs 3.80

        assertThat(service.summary(FiatCurrency.USD).orElseThrow().change().h24().pct())
                .isEqualByComparingTo("1.0000");
        assertThat(service.summary(FiatCurrency.PLN).orElseThrow().change().h24().pct())
                .isEqualByComparingTo("3.7297");
    }

    @Test
    void changeIsNullWhenTheEarlierTotalWasZero() {
        storeTotals(NOW.minusSeconds(24 * 3600), "0", "0");
        storeTotals(NOW, "100", "370");

        assertThat(service.summary(FiatCurrency.USD).orElseThrow().change().h24()).isNull();
    }

    @Test
    void negativeChangeKeepsItsSign() {
        storeTotals(NOW.minusSeconds(24 * 3600), "10000", "37000");
        storeTotals(NOW, "9000", "33300");

        assertThat(service.summary(FiatCurrency.USD).orElseThrow().change().h24().abs())
                .isEqualByComparingTo("-1000.00");
        assertThat(service.summary(FiatCurrency.USD).orElseThrow().change().h24().pct())
                .isEqualByComparingTo("-10.0000");
    }

    @Test
    void athIsFoundPerCurrencyAndMayFallOnDifferentDays() {
        storeTotals(Instant.parse("2026-05-07T05:00:00Z"), "52213.54", "180000");  // szczyt USD
        storeTotals(Instant.parse("2026-06-18T13:00:00Z"), "50000", "196139.18");  // szczyt PLN
        storeTotals(NOW, "47899.88", "177229.55");

        assertThat(service.summary(FiatCurrency.USD).orElseThrow().ath())
                .satisfies(ath -> {
                    assertThat(ath.value()).isEqualByComparingTo("52213.54");
                    assertThat(ath.at()).isEqualTo(Instant.parse("2026-05-07T05:00:00Z"));
                });
        assertThat(service.summary(FiatCurrency.PLN).orElseThrow().ath())
                .satisfies(ath -> {
                    assertThat(ath.value()).isEqualByComparingTo("196139.18");
                    assertThat(ath.at()).isEqualTo(Instant.parse("2026-06-18T13:00:00Z"));
                });
    }

    @Test
    void athIsNeverBelowTheCurrentTotal() {
        storeTotals(NOW, "100", "370");

        PortfolioSummaryResponse summary = service.summary(FiatCurrency.USD).orElseThrow();

        assertThat(summary.ath().value()).isGreaterThanOrEqualTo(summary.total());
    }

    @Test
    void sourcesAreEmptyOnlyWhenPositionsAre() {
        // front rozpoznaje pusty portfel po sources.length === 0, więc ten niezmiennik
        // musi trzymać: każda pozycja wnosi swoje źródło, nigdy nie ma pozycji bez źródeł
        store(NOW, true, position("Rabby", "ethereum", "ETH", "2", "3000", false));
        PortfolioSummaryResponse withPositions = service.summary(FiatCurrency.USD).orElseThrow();
        assertThat(withPositions.positions()).isNotEmpty();
        assertThat(withPositions.sources()).isNotEmpty();

        repository.clear();
        storeTotals(NOW, "0", "0");
        PortfolioSummaryResponse empty = service.summary(FiatCurrency.USD).orElseThrow();
        assertThat(empty.positions()).isEmpty();
        assertThat(empty.sources()).isEmpty();
    }

    @Test
    void sharesAreZeroNotNullWhenTheTotalIsZero() {
        // pozycja warta 0 przy total 0 to dzielenie 0/0 — pct musi wyjść 0, nie null ani NaN
        store(NOW, true, position("Rabby", "ethereum", "DEAD", "1000", "0", false));

        PortfolioSummaryResponse summary = service.summary(FiatCurrency.USD).orElseThrow();

        assertThat(summary.total()).isEqualByComparingTo("0.00");
        assertThat(summary.positions()).singleElement().satisfies(positionDto -> {
            assertThat(positionDto.pct()).isNotNull().isEqualByComparingTo("0");
            assertThat(positionDto.price()).isNotNull();
            assertThat(positionDto.value()).isEqualByComparingTo("0");
        });
        assertThat(summary.sources()).singleElement()
                .satisfies(source -> assertThat(source.pct()).isNotNull().isEqualByComparingTo("0"));
    }

    @Test
    void historyIsAnEmptySeriesWithoutSnapshots() {
        PortfolioHistoryResponse history = service.history(HistoryRange.D30, FiatCurrency.USD);

        assertThat(history.range()).isEqualTo(HistoryRange.D30);
        assertThat(history.interval()).isEqualTo(HistoryInterval.H4);
        assertThat(history.points()).isEmpty();
    }

    @Test
    void historyPicksTheIntervalFromTheRange() {
        storeTotals(NOW, "100", "370");

        assertThat(service.history(HistoryRange.H24, FiatCurrency.USD).interval()).isEqualTo(HistoryInterval.H1);
        assertThat(service.history(HistoryRange.D7, FiatCurrency.USD).interval()).isEqualTo(HistoryInterval.H1);
        assertThat(service.history(HistoryRange.D30, FiatCurrency.USD).interval()).isEqualTo(HistoryInterval.H4);
        assertThat(service.history(HistoryRange.D90, FiatCurrency.USD).interval()).isEqualTo(HistoryInterval.D1);
        assertThat(service.history(HistoryRange.Y1, FiatCurrency.USD).interval()).isEqualTo(HistoryInterval.D1);
        assertThat(service.history(HistoryRange.ALL, FiatCurrency.USD).interval()).isEqualTo(HistoryInterval.D1);
    }

    @Test
    void historyWindowEndsAtTheLatestSnapshotAndDropsOlderOnes() {
        storeTotals(NOW.minusSeconds(26 * 3600), "90", "333");   // poza okno 24h
        storeTotals(NOW.minusSeconds(23 * 3600), "95", "351.5");
        storeTotals(NOW, "100", "370");

        PortfolioHistoryResponse history = service.history(HistoryRange.H24, FiatCurrency.USD);

        assertThat(history.points()).extracting(point -> point.t().toString())
                .containsExactly("2026-10-03T08:00:00Z", "2026-10-04T07:00:00Z");
    }

    @Test
    void aFullDayOfHourlySnapshotsYieldsAtMost24Points() {
        // the contract documents "up to 24"; a closed lower bound would give 25
        for (int hoursAgo = 30; hoursAgo >= 0; hoursAgo--) {
            storeTotals(NOW.minusSeconds(hoursAgo * 3600L), "100", "370");
        }

        PortfolioHistoryResponse history = service.history(HistoryRange.H24, FiatCurrency.USD);

        assertThat(history.points()).hasSize(24);
        assertThat(history.points().getFirst().t()).isEqualTo(NOW.minusSeconds(23 * 3600));
        assertThat(history.points().getLast().t()).isEqualTo(NOW);
    }

    @Test
    void allRangeStartsAtTheFirstSnapshot() {
        storeTotals(Instant.parse("2025-01-01T00:00:00Z"), "10", "37");
        storeTotals(NOW, "100", "370");

        assertThat(service.history(HistoryRange.ALL, FiatCurrency.USD).points())
                .extracting(point -> point.t().toString())
                .containsExactly("2025-01-01T00:00:00Z", "2026-10-04T07:00:00Z");
    }

    @Test
    void historyCarriesTheCompletenessOfEachSnapshot() {
        store(NOW.minusSeconds(3600), false, position("Rabby", "ethereum", "ETH", "1", "100", true));
        store(NOW, true, position("Rabby", "ethereum", "ETH", "1", "110", false));

        assertThat(service.history(HistoryRange.H24, FiatCurrency.USD).points())
                .extracting(point -> point.complete())
                .containsExactly(false, true);
    }

    @Test
    void lastHistoryPointIsTheSameSnapshotAsSummary() {
        storeTotals(NOW.minusSeconds(2 * 3600), "90", "333");
        store(NOW, false,
                position("Rabby", "ethereum", "ETH", "2", "3000", false),
                position("Trezor", "bsc", "BNB", "10", "300", true));

        for (FiatCurrency currency : FiatCurrency.values()) {
            PortfolioSummaryResponse summary = service.summary(currency).orElseThrow();
            for (HistoryRange range : HistoryRange.values()) {
                var last = service.history(range, currency).points().getLast();

                assertThat(last.t()).as("t dla %s/%s", range, currency).isEqualTo(summary.takenAt());
                assertThat(last.value()).as("value dla %s/%s", range, currency)
                        .isEqualByComparingTo(summary.total());
                assertThat(last.complete()).as("complete dla %s/%s", range, currency)
                        .isEqualTo(summary.complete());
            }
        }
    }

    @Test
    void historyIgnoresPositionsAndReadsOnlyTotals() {
        Optional<PortfolioSnapshot> stored = Optional.of(store(NOW, true,
                position("Rabby", "ethereum", "ETH", "2", "3000", false)));

        assertThat(service.history(HistoryRange.H24, FiatCurrency.USD).points())
                .singleElement()
                .satisfies(point -> assertThat(point.value())
                        .isEqualByComparingTo(stored.orElseThrow().totalUsd()));
    }
}

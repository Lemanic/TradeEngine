package pl.tradeengine.application.service.portfolio;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.portfolio.HistoryInterval;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioMetricsTest {

    private static SnapshotHeader at(String isoInstant, String totalUsd) {
        return new SnapshotHeader(Instant.parse(isoInstant), new BigDecimal(totalUsd),
                new BigDecimal(totalUsd).multiply(new BigDecimal("3.7")), true);
    }

    @Test
    void roundsMoneyToTwoPlacesHalfUp() {
        assertThat(PortfolioMetrics.money(new BigDecimal("47899.875"))).isEqualTo(new BigDecimal("47899.88"));
        assertThat(PortfolioMetrics.money(new BigDecimal("47899.874"))).isEqualTo(new BigDecimal("47899.87"));
        assertThat(PortfolioMetrics.money(new BigDecimal("0"))).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void expressesSharesInPercentagePointsWithFourPlaces() {
        // the three shares from the contract example, against its total of 47899.88
        BigDecimal total = new BigDecimal("47899.88");

        assertThat(PortfolioMetrics.pct(new BigDecimal("20198.58"), total))
                .isEqualByComparingTo("42.1683");
        assertThat(PortfolioMetrics.pct(new BigDecimal("15300.06"), total))
                .isEqualByComparingTo("31.9418");
        assertThat(PortfolioMetrics.pct(new BigDecimal("9001.35"), total))
                .isEqualByComparingTo("18.7920");
        assertThat(PortfolioMetrics.pct(new BigDecimal("9001.35"), total).scale()).isEqualTo(4);
    }

    @Test
    void treatsAZeroPortfolioAsZeroSharesRatherThanDividingByZero() {
        assertThat(PortfolioMetrics.pct(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void keepsAmountsAtFullPrecisionWithoutPaddingOrExponent() {
        assertThat(PortfolioMetrics.amount(new BigDecimal("2.410000000000000000")).toPlainString())
                .isEqualTo("2.41");
        assertThat(PortfolioMetrics.amount(new BigDecimal("6390.281180127170627929")).toPlainString())
                .isEqualTo("6390.281180127170627929");
        assertThat(PortfolioMetrics.amount(new BigDecimal("1500.000000000000000000")).toPlainString())
                .isEqualTo("1500");
        assertThat(PortfolioMetrics.amount(new BigDecimal("0.000000000000000000")).toPlainString())
                .isEqualTo("0");
    }

    @Test
    void keepsSubUnitPricesPreciseAndRoundsTheRestToCents() {
        // two places would turn NPC into 0.02 — a double-digit error
        assertThat(PortfolioMetrics.price(new BigDecimal("0.022091374")).toPlainString())
                .isEqualTo("0.0220914");
        assertThat(PortfolioMetrics.price(new BigDecimal("0.013764")).toPlainString())
                .isEqualTo("0.013764");
        assertThat(PortfolioMetrics.price(new BigDecimal("2698.224168"))).isEqualByComparingTo("2698.22");
        assertThat(PortfolioMetrics.price(new BigDecimal("1.0"))).isEqualByComparingTo("1.00");
        assertThat(PortfolioMetrics.price(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void takesTheLastSnapshotOfEachInterval() {
        List<SnapshotHeader> headers = List.of(
                at("2026-10-04T00:00:00Z", "100"),
                at("2026-10-04T01:00:00Z", "110"),
                at("2026-10-04T02:00:00Z", "120"),
                at("2026-10-04T03:00:00Z", "130"),   // last of the 00:00–04:00 bucket
                at("2026-10-04T04:00:00Z", "140"),
                at("2026-10-04T07:00:00Z", "170"));  // last of the 04:00–08:00 bucket

        List<SnapshotHeader> sampled = PortfolioMetrics.sample(headers, HistoryInterval.H4);

        assertThat(sampled).extracting(SnapshotHeader::takenAt)
                .containsExactly(Instant.parse("2026-10-04T03:00:00Z"),
                        Instant.parse("2026-10-04T07:00:00Z"));
    }

    @Test
    void skipsEmptyIntervalsInsteadOfFillingThem() {
        List<SnapshotHeader> headers = List.of(
                at("2026-10-01T05:00:00Z", "100"),
                // 2 i 3 października bez snapshotow
                at("2026-10-04T09:00:00Z", "140"),
                at("2026-10-04T23:00:00Z", "150"));

        List<SnapshotHeader> sampled = PortfolioMetrics.sample(headers, HistoryInterval.D1);

        assertThat(sampled).extracting(SnapshotHeader::takenAt)
                .containsExactly(Instant.parse("2026-10-01T05:00:00Z"),
                        Instant.parse("2026-10-04T23:00:00Z"));
    }

    @Test
    void alignsBucketsToUtcBoundaries() {
        // 23:00 and 00:00 fall into different days even though they are one hour apart
        List<SnapshotHeader> headers = List.of(
                at("2026-10-03T23:00:00Z", "100"),
                at("2026-10-04T00:00:00Z", "110"));

        assertThat(PortfolioMetrics.sample(headers, HistoryInterval.D1)).hasSize(2);
        assertThat(PortfolioMetrics.sample(headers, HistoryInterval.H4)).hasSize(2);
        assertThat(PortfolioMetrics.sample(headers, HistoryInterval.H1)).hasSize(2);
    }

    @Test
    void keepsOnePointPerHourAtHourlyResolution() {
        List<SnapshotHeader> headers = List.of(
                at("2026-10-04T06:00:00Z", "100"),
                at("2026-10-04T07:00:00Z", "110"));

        assertThat(PortfolioMetrics.sample(headers, HistoryInterval.H1))
                .extracting(SnapshotHeader::takenAt)
                .containsExactly(Instant.parse("2026-10-04T06:00:00Z"),
                        Instant.parse("2026-10-04T07:00:00Z"));
    }

    @Test
    void handlesAnEmptyWindow() {
        assertThat(PortfolioMetrics.sample(List.of(), HistoryInterval.H1)).isEmpty();
    }

    @Test
    void sortsInputBeforeSampling() {
        List<SnapshotHeader> unordered = List.of(
                at("2026-10-04T07:00:00Z", "170"),
                at("2026-10-04T01:00:00Z", "110"));

        assertThat(PortfolioMetrics.sample(unordered, HistoryInterval.D1))
                .singleElement()
                .extracting(SnapshotHeader::takenAt)
                .isEqualTo(Instant.parse("2026-10-04T07:00:00Z"));
    }
}

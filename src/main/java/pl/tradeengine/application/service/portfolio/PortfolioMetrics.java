package pl.tradeengine.application.service.portfolio;

import pl.tradeengine.domain.model.portfolio.HistoryInterval;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rounding and arithmetic of the API contract, kept free of Spring and of the repository so the
 * rules can be tested on their own.
 *
 * <p>Precision, per docs/portfolio-api.md decision 7: money to 2 places, {@code pct} to 4 and in
 * percentage points, {@code amount} at full precision.
 */
public final class PortfolioMetrics {

    private static final int MONEY_SCALE = 2;
    /** Sub-dollar prices need more than cents to stay truthful; see {@link #price(BigDecimal)}. */
    private static final MathContext SUB_UNIT_PRICE = new MathContext(6, RoundingMode.HALF_UP);
    private static final int PCT_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PortfolioMetrics() {}

    /** A money amount as the API returns it. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Share of {@code whole} in percentage points. A zero {@code whole} yields {@code 0.0000}
     * rather than an error — an empty portfolio has no shares, not undefined ones.
     */
    public static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.UNNECESSARY);
        }
        return part.multiply(HUNDRED).divide(whole, PCT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * A unit price. Two places above 1.00, six significant digits below it.
     *
     * <p>This is the one place where the contract's blanket "money rounds to 2 places" would lie:
     * NPC trades around $0.022 and WOO around $0.014, so two places would turn them into $0.02
     * and $0.01 — a double-digit error, and inconsistent with {@code value = amount x price}.
     * The dashboard's own {@code price()} formatter already renders four decimals below $1,
     * so it expects this precision.
     */
    public static BigDecimal price(BigDecimal value) {
        if (value.abs().compareTo(BigDecimal.ONE) >= 0) {
            return money(value);
        }
        return strip(value.round(SUB_UNIT_PRICE));
    }

    /**
     * An asset amount: full precision, but without the trailing zeros that the {@code numeric(38,18)}
     * column adds, and never in scientific notation.
     */
    public static BigDecimal amount(BigDecimal value) {
        return strip(value);
    }

    /** Drops padding zeros without ever falling back to scientific notation. */
    private static BigDecimal strip(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    /**
     * One point per interval, each being the <em>last</em> snapshot of its interval; intervals
     * with no snapshot are skipped, never interpolated. Buckets are aligned to UTC epoch
     * boundaries, so a 4h bucket starts at 00:00, 04:00, … UTC.
     *
     * @param headers snapshots in the window, any order
     */
    public static List<SnapshotHeader> sample(List<SnapshotHeader> headers, HistoryInterval interval) {
        long step = interval.duration().getSeconds();
        Map<Long, SnapshotHeader> lastPerBucket = new LinkedHashMap<>();

        headers.stream()
                .sorted(Comparator.comparing(SnapshotHeader::takenAt))
                .forEach(header -> lastPerBucket.put(
                        Math.floorDiv(header.takenAt().getEpochSecond(), step), header));

        return List.copyOf(lastPerBucket.values());
    }
}

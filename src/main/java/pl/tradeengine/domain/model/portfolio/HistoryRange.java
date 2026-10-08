package pl.tradeengine.domain.model.portfolio;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/** Requested history window, with the resolution it is sampled at. */
public enum HistoryRange {

    H24("24H", Duration.ofHours(24), HistoryInterval.H1),
    D7("7D", Duration.ofDays(7), HistoryInterval.H1),
    D30("30D", Duration.ofDays(30), HistoryInterval.H4),
    D90("90D", Duration.ofDays(90), HistoryInterval.D1),
    Y1("1Y", Duration.ofDays(365), HistoryInterval.D1),
    /** From the very first snapshot — no fixed look-back. */
    ALL("ALL", null, HistoryInterval.D1);

    private final String code;
    private final Duration lookback;
    private final HistoryInterval interval;

    HistoryRange(String code, Duration lookback, HistoryInterval interval) {
        this.code = code;
        this.lookback = lookback;
        this.interval = interval;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** Empty for {@link #ALL}, which starts at the oldest snapshot instead. */
    public Optional<Duration> lookback() {
        return Optional.ofNullable(lookback);
    }

    public HistoryInterval interval() {
        return interval;
    }

    /** Matches the wire code exactly — {@code 24h} is not accepted, only {@code 24H}. */
    public static Optional<HistoryRange> fromCode(String code) {
        return Arrays.stream(values())
                .filter(range -> range.code.equals(code))
                .findFirst();
    }

    public static String codes() {
        return String.join(", ", Arrays.stream(values()).map(HistoryRange::code).toList());
    }
}

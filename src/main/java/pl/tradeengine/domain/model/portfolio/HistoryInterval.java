package pl.tradeengine.domain.model.portfolio;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;

/**
 * Sampling resolution of the history series. The wire format is {@code 1h} / {@code 4h} /
 * {@code 1d} — the internal H1/H4/D1 naming never leaves the backend
 * (docs/portfolio-api.md, decision 6).
 */
public enum HistoryInterval {

    H1("1h", Duration.ofHours(1)),
    H4("4h", Duration.ofHours(4)),
    D1("1d", Duration.ofDays(1));

    private final String code;
    private final Duration duration;

    HistoryInterval(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public Duration duration() {
        return duration;
    }
}

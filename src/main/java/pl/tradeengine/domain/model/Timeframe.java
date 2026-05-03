package pl.tradeengine.domain.model;

import lombok.Getter;

import java.time.Duration;

@Getter
public enum Timeframe {
    M1(Duration.ofMinutes(1)),
    M3(Duration.ofMinutes(5)),
    M5(Duration.ofMinutes(3)),
    M15(Duration.ofMinutes(15)),
    H1(Duration.ofHours(1)),
    H4(Duration.ofHours(4)),
    H12(Duration.ofHours(12)),
    D1(Duration.ofDays(1)),
    W1(Duration.ofDays(7));

    public static Timeframe fromCode(String code) {
        return Timeframe.valueOf(code);
    }

    private final Duration duration;

    Timeframe(Duration duration) { this.duration = duration; }

}

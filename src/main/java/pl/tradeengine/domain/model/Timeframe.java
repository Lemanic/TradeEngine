package pl.tradeengine.domain.model;

public enum Timeframe {
    M5, M15, H1, H4, H12, D1, W1;

    public static Timeframe fromCode(String code) {
        return Timeframe.valueOf(code);
    }
}

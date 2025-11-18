package pl.tradeengine.domain.model;

public enum Direction {
    LONG,
    SHORT;

    public static Direction fromSignal(String value) {
        return Direction.valueOf(value.toUpperCase());
    }

}
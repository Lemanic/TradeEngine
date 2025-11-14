package pl.tradeengine.alerts.domain;

public record IncomingAlert(
        String symbol,
        String interval,
        SignalType signalType,
        Direction direction,
        double strength
) {}

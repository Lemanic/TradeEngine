package pl.tradeengine.alerts.domain;

public record IncomingAlert(
        String symbol,
        String timeframe,
        SignalType signalType,
        Direction direction,
        double strength
) {}
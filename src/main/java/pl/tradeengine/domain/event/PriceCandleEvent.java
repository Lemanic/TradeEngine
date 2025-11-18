package pl.tradeengine.domain.event;

import pl.tradeengine.domain.model.PriceCandle;

public final class PriceCandleEvent implements DomainEvent {

    private final PriceCandle candle;

    public PriceCandleEvent(PriceCandle candle) {
        this.candle = candle;
    }

    public PriceCandle candle() {
        return candle;
    }
}
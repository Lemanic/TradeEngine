package pl.tradeengine.domain.scenario;

import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.PriceCandle;

import java.util.List;

class KeyLevelDivergenceScenario implements Scenario {

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (!(event instanceof PriceCandleEvent priceEvent)) {
            return List.of();
        }
        PriceCandle candle = priceEvent.candle();
        return null;
    }

    @Override
    public Long id() {
        return 0L;
    }

    @Override
    public String name() {
        return "";
    }

}

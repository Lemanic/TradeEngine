package pl.tradeengine.application.service.fill;

import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.port.fill.TradeFillSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Oddaje zaprogramowane fille i zapamietuje, od jakiego momentu byl pytany — bez tego nie da sie
 * sprawdzic, czy kursor zostal uzyty, czy przebieg znowu zaczal od bootstrapu.
 */
public class FakeTradeFillSource implements TradeFillSource {

    private final List<Instant> askedSince = new ArrayList<>();
    private List<TradeFill> fills = List.of();
    private RuntimeException failure;

    @Override
    public List<TradeFill> fillsSince(Instant since) {
        askedSince.add(since);
        if (failure != null) {
            throw failure;
        }
        return fills;
    }

    public void returns(TradeFill... programmed) {
        this.fills = List.of(programmed);
    }

    public void returns(List<TradeFill> programmed) {
        this.fills = List.copyOf(programmed);
    }

    public void fails(RuntimeException exception) {
        this.failure = exception;
    }

    public List<Instant> askedSince() {
        return List.copyOf(askedSince);
    }
}

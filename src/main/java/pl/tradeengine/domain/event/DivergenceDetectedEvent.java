package pl.tradeengine.domain.event;

import pl.tradeengine.domain.model.DivergenceSignal;

public final class DivergenceDetectedEvent implements DomainEvent {

    private final DivergenceSignal signal;

    public DivergenceDetectedEvent(DivergenceSignal signal) {
        this.signal = signal;
    }

    public DivergenceSignal signal() {
        return signal;
    }
}
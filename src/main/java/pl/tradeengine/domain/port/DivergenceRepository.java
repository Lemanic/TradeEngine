package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.DivergenceSignal;

public interface DivergenceRepository {
    DivergenceSignal save(DivergenceSignal signal);
}

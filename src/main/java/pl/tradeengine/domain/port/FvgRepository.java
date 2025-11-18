package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.FvgZone;

public interface FvgRepository {
    FvgZone save(FvgZone fvgZone);
}

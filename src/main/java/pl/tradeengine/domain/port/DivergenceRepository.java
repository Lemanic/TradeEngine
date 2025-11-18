package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface DivergenceRepository {
    DivergenceSignal save(DivergenceSignal signal);
    Optional<DivergenceSignal> findMostRecent(Symbol symbol, Timeframe timeframe, ZonedDateTime since);

}

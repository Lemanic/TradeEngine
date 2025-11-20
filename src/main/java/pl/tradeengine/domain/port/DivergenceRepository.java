package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface DivergenceRepository {

    DivergenceSignal save(DivergenceSignal signal);

    Optional<DivergenceSignal> findMostRecent(Symbol symbol, Timeframe timeframe, ZonedDateTime since);

    Optional<DivergenceSignal> findMostRecentByDirection(Symbol symbol, Timeframe timeframe, Direction direction, ZonedDateTime since);
    List<DivergenceSignal> findAllByDirectionSince(Symbol symbol, Timeframe timeframe, Direction direction, ZonedDateTime since);

}

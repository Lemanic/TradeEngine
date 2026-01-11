package pl.tradeengine.domain.port;

import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.application.dto.SwingPointDto; // lub model domenowy
import java.time.ZonedDateTime;
import java.util.List;

public interface SwingPointRepository {
    void save(Symbol symbol, Timeframe timeframe, String type, java.math.BigDecimal price, ZonedDateTime detectedAt);

    List<SwingPointDto> findRecentSwings(Symbol symbol, Timeframe timeframe, String type, ZonedDateTime since);
}

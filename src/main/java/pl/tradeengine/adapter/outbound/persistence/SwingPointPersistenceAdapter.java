package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.application.dto.SwingPointDto;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.SwingPointRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
public class SwingPointPersistenceAdapter implements SwingPointRepository {

    private final JpaSwingPointRepository jpaRepo;

    public SwingPointPersistenceAdapter(JpaSwingPointRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void save(Symbol symbol, Timeframe timeframe, String type, BigDecimal price, ZonedDateTime detectedAt) {
        SwingPointEntity entity = new SwingPointEntity(
                symbol.code(),
                timeframe,
                type,
                price,
                detectedAt
        );
        jpaRepo.save(entity);
    }

    @Override
    public List<SwingPointDto> findRecentSwings(Symbol symbol, Timeframe timeframe, String type, ZonedDateTime since) {
        return jpaRepo.findRecent(symbol.code(), timeframe, type, since)
                .stream()
                .map(e -> new SwingPointDto(
                        e.getSymbol(),
                        e.getTimeframe().name(),
                        e.getType(),
                        e.getPrice()
                ))
                .toList();
    }
}

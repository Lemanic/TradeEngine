package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.DivergenceRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public class DivergencePersistenceAdapter implements DivergenceRepository {

    private final JpaDivergenceRepository jpaRepository;

    public DivergencePersistenceAdapter(JpaDivergenceRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DivergenceSignal save(DivergenceSignal signal) {
        DivergenceEntity entity = new DivergenceEntity(
                signal.getId(),
                signal.getSymbol().code(),
                signal.getTimeframe(),
                signal.getDirection(),
                signal.getStrength(),
                signal.getDetectedAt()
        );
        DivergenceEntity saved = jpaRepository.save(entity);
        return new DivergenceSignal(
                saved.getId(),
                new Symbol(saved.getSymbol()),
                saved.getTimeframe(),
                saved.getDirection(),
                saved.getStrength(),
                saved.getDetectedAt()
        );
    }

    @Override
    public Optional<DivergenceSignal> findMostRecent(Symbol symbol, Timeframe timeframe, ZonedDateTime since) {
        Optional<DivergenceEntity> entityOpt = jpaRepository.findFirstBySymbolAndTimeframeAndDetectedAtAfterOrderByDetectedAtDesc(
                symbol.code(), timeframe, since
        );

        return entityOpt.map(this::toDomain);
    }

    private DivergenceSignal toDomain(DivergenceEntity entity) {
        return new DivergenceSignal(
                entity.getId(),
                new Symbol(entity.getSymbol()),
                entity.getTimeframe(),
                entity.getDirection(),
                entity.getStrength(),
                entity.getDetectedAt()
        );
    }
}

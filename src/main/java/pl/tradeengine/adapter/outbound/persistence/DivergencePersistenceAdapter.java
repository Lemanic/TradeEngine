package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.port.DivergenceRepository;

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

}

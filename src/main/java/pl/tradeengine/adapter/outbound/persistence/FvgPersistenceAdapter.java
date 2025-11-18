package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.port.FvgRepository;

// pl.tradeengine.adapter.outbound.persistence.FvgPersistenceAdapter
@Repository
public class FvgPersistenceAdapter implements FvgRepository {

    private final JpaFvgRepository jpaRepository;

    public FvgPersistenceAdapter(JpaFvgRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FvgZone save(FvgZone fvg) {
        FvgEntity entity = toEntity(fvg);
        FvgEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private FvgEntity toEntity(FvgZone fvg) {
        return new FvgEntity(
                fvg.getId(),                          // jeśli masz id() w domenie, inaczej null
                fvg.getSymbol().code(),               // np. String w VO Symbol
                fvg.getTimeframe(),
                fvg.getDirection(),
                fvg.getLowerPrice(),
                fvg.getUpperPrice(),
                fvg.getKind(),
                fvg.getStatus()
        );
    }

    private FvgZone toDomain(FvgEntity e) {
        Symbol symbol = new Symbol(e.getSymbol());
        return new FvgZone(
                e.getId(),
                symbol,
                e.getTimeframe(),
                e.getDirection(),
                e.getLowerPrice(),
                e.getUpperPrice(),
                e.getKind(),
                e.getStatus()
        );
    }
}

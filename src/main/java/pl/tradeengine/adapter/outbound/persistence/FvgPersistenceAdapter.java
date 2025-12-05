package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.*;
import pl.tradeengine.domain.port.FvgRepository;

import java.math.BigDecimal;
import java.util.List;

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
                fvg.getId(),
                fvg.getSymbol().code(),
                fvg.getTimeframe(),
                fvg.getDirection(),
                fvg.getLowerPrice(),
                fvg.getUpperPrice(),
                fvg.getStrength(),
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
                e.getStrength(),
                e.getKind(),
                e.getStatus()
        );
    }

    @Override
    public List<FvgZone> findIntersectingOpenFvgs(Symbol symbol, BigDecimal low, BigDecimal high) {
        List<FvgEntity> entities = jpaRepository.findIntersectingForAllTimeframes(
                symbol.code(), low, high
        );
        return entities.stream()
                .map(this::toDomain)
                .toList();
    }


    @Override
    public void updateStatus(Long fvgId, FvgStatus newStatus) {
        jpaRepository.updateStatus(fvgId, newStatus);
    }

    @Override
    public List<FvgZone> findTouched(Symbol symbol, Timeframe timeframe) {
        List<FvgEntity> entities = jpaRepository.findBySymbolAndTimeframeAndStatus(
                symbol.code(), timeframe, FvgStatus.TOUCHED
        );
        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<FvgZone> findActiveForSymbolAndDirectionOnHigherTf(
            Symbol symbol,
            Direction direction,
            List<FvgStatus> statuses,
            List<Timeframe> timeframes
    ) {
        List<FvgEntity> entities = jpaRepository
                .findBySymbolAndDirectionAndStatusInAndTimeframeIn(
                        symbol.code(),
                        direction,
                        statuses,
                        timeframes
                );

        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

}

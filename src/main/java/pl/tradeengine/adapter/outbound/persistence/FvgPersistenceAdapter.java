package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.AlertMode;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.FvgRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
        FvgEntity fvgEntity = new FvgEntity(
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

        fvgEntity.setAlertMode(fvg.getAlertMode());
        fvgEntity.setTouchedAt(fvg.getTouchedAt());
        fvgEntity.setLeftZoneAt(fvg.getLeftZoneAt());
        fvgEntity.setFilledAt(fvg.getFilledAt());
        fvgEntity.setExpiresAt(fvg.getExpiresAt());
        return fvgEntity;
    }


    private FvgZone toDomain(FvgEntity entity) {
        FvgZone fvgZone = new FvgZone(
                entity.getId(),
                new Symbol(entity.getSymbol()),
                entity.getTimeframe(),
                entity.getDirection(),
                entity.getLowerPrice(),
                entity.getUpperPrice(),
                entity.getStrength(),
                entity.getKind(),
                entity.getStatus()
        );

        fvgZone.setAlertMode(entity.getAlertMode());
        fvgZone.setTouchedAt(entity.getTouchedAt());
        fvgZone.setLeftZoneAt(entity.getLeftZoneAt());
        fvgZone.setFilledAt(entity.getFilledAt());
        fvgZone.setExpiresAt(entity.getExpiresAt());
        return fvgZone;
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

    @Override
    public void markTouched(Long fvgId, ZonedDateTime touchedAt) {
        jpaRepository.markTouched(fvgId, touchedAt);
    }

    @Override
    public void setLeftZoneAt(Long fvgId, ZonedDateTime leftZoneAt) {
        jpaRepository.setLeftZoneAt(fvgId, leftZoneAt);
    }

    @Override
    public void setAlertMode(Long fvgId, AlertMode mode) {
        jpaRepository.setAlertMode(fvgId, mode);
    }

    @Override
    public void resumeArmed(Long fvgId) {
        jpaRepository.resumeArmed(fvgId);
    }

    @Override
    public void markFilled(Long fvgId, ZonedDateTime filledAt, ZonedDateTime expiresAt) {
        jpaRepository.markFilled(fvgId, filledAt, expiresAt);
    }

    @Override
    public int consumeExpiredFilled(ZonedDateTime now) {
        return jpaRepository.consumeExpiredFilled(now);
    }

    @Override
    public List<FvgZone> findTouchedForSymbolOnTimeframes(Symbol symbol, List<Timeframe> timeframes) {
        return jpaRepository.findTouchedHtfForSymbol(symbol.code(), timeframes)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<FvgZone> findById(Long id) {
        return Optional.empty();
    }
}

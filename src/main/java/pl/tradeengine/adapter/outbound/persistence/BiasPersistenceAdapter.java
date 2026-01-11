package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.BiasRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

interface JpaBiasRepo extends JpaRepository<BiasEntity, Long> {
    Optional<BiasEntity> findBySymbolAndTimeframe(String symbol, Timeframe timeframe);
}

@Repository
public class BiasPersistenceAdapter implements BiasRepository {

    private final JpaBiasRepo jpaRepo;

    public BiasPersistenceAdapter(JpaBiasRepo jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void updateBias(Symbol symbol, Timeframe timeframe, BiasStatus status, String reason) {
        BiasEntity entity = jpaRepo.findBySymbolAndTimeframe(symbol.code(), timeframe)
                .orElse(new BiasEntity(symbol.code(), timeframe, status, reason, ZonedDateTime.now()));

        entity.setStatus(status);
        entity.setLastReason(reason);
        entity.setUpdatedAt(ZonedDateTime.now());

        jpaRepo.save(entity);
    }

    @Override
    public BiasStatus getBias(Symbol symbol, Timeframe timeframe) {
        return jpaRepo.findBySymbolAndTimeframe(symbol.code(), timeframe)
                .map(BiasEntity::getStatus)
                .orElse(BiasStatus.NEUTRAL);
    }
}

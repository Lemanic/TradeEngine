package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.AlertMuteRepository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class AlertMutePersistenceAdapter implements AlertMuteRepository {

    /** Stands in for the all-timeframes rule inside the key; no timeframe is named like this. */
    private static final String ALL_TIMEFRAMES = "*";

    private final JpaAlertMuteRepository jpaRepository;

    public AlertMutePersistenceAdapter(JpaAlertMuteRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<AlertMute> findAll() {
        return jpaRepository.findAll().stream().map(AlertMutePersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public AlertMute save(AlertMute mute) {
        AlertMuteEntity entity = new AlertMuteEntity();
        entity.setKey(key(mute.strategy(), mute.symbol(), mute.timeframe()));
        entity.setStrategy(mute.strategy());
        entity.setSymbol(mute.symbol().code());
        entity.setTimeframe(mute.timeframe());
        entity.setMutedAt(mute.mutedAt().atOffset(ZoneOffset.UTC));
        entity.setMutedBy(mute.mutedBy());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(String strategy, Symbol symbol, Timeframe timeframe) {
        jpaRepository.deleteById(key(strategy, symbol, timeframe));
    }

    static String key(String strategy, Symbol symbol, Timeframe timeframe) {
        return strategy + "|" + symbol.code() + "|" + (timeframe == null ? ALL_TIMEFRAMES : timeframe.name());
    }

    private static AlertMute toDomain(AlertMuteEntity entity) {
        return new AlertMute(entity.getStrategy(), new Symbol(entity.getSymbol()),
                entity.getTimeframe(), entity.getMutedAt().toInstant(), entity.getMutedBy());
    }
}

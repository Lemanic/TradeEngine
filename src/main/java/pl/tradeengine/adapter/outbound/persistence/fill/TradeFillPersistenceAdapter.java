package pl.tradeengine.adapter.outbound.persistence.fill;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.port.fill.TradeFillRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class TradeFillPersistenceAdapter implements TradeFillRepository {

    /** Tyle, ile ma kolumna. Nazwa dluzsza niz to jest obcinana — patrz markSeen(). */
    private static final int COIN_COLUMN_LENGTH = 64;

    private final JpaTradeFillRepository jpaRepository;

    public TradeFillPersistenceAdapter(JpaTradeFillRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Instant> lastFillTime() {
        return jpaRepository.findTopByOrderByFillTimeDesc()
                .map(entity -> entity.getFillTime().toInstant());
    }

    @Override
    public boolean isNew(long tid) {
        return !jpaRepository.existsById(tid);
    }

    @Override
    @Transactional
    public void markSeen(TradeFill fill, Instant notifiedAt) {
        TradeFillEntity entity = new TradeFillEntity();
        entity.setTid(fill.tid());
        entity.setFillTime(fill.time().atOffset(ZoneOffset.UTC));
        // Nazwa rynku dluzsza niz kolumna wywrocilaby INSERT, a wtedy fill nigdy nie zostalby
        // uznany za obsluzony i alert o nim wracalby w kazdym przebiegu.
        entity.setCoin(fill.coin().length() > COIN_COLUMN_LENGTH
                ? fill.coin().substring(0, COIN_COLUMN_LENGTH)
                : fill.coin());
        entity.setNotifiedAt(notifiedAt.atOffset(ZoneOffset.UTC));
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return jpaRepository.deleteByFillTimeBefore(cutoff.atOffset(ZoneOffset.UTC)).size();
    }
}

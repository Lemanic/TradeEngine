package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.tradeengine.domain.port.portfolio.ManualHoldingRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class ManualHoldingPersistenceAdapter implements ManualHoldingRepository {

    private final JpaManualHoldingRepository jpaRepository;

    public ManualHoldingPersistenceAdapter(JpaManualHoldingRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<StoredAmount> find(String key) {
        return jpaRepository.findById(key).map(ManualHoldingPersistenceAdapter::toDomain);
    }

    @Override
    public Map<String, StoredAmount> findAll() {
        Map<String, StoredAmount> all = new LinkedHashMap<>();
        jpaRepository.findAll().forEach(entity -> all.put(entity.getKey(), toDomain(entity)));
        return all;
    }

    @Override
    @Transactional
    public StoredAmount save(String key, BigDecimal amount, Instant updatedAt) {
        ManualHoldingEntity entity = jpaRepository.findById(key).orElseGet(ManualHoldingEntity::new);
        entity.setKey(key);
        entity.setAmount(amount);
        entity.setUpdatedAt(updatedAt.atOffset(ZoneOffset.UTC));
        return toDomain(jpaRepository.save(entity));
    }

    private static StoredAmount toDomain(ManualHoldingEntity entity) {
        return new StoredAmount(entity.getAmount(), entity.getUpdatedAt().toInstant());
    }
}

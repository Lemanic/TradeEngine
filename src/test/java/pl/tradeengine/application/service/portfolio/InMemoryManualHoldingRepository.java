package pl.tradeengine.application.service.portfolio;

import pl.tradeengine.domain.port.portfolio.ManualHoldingRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Fake zgodny z konwencja repo: repozytoriow nie mockujemy. */
public class InMemoryManualHoldingRepository implements ManualHoldingRepository {

    private final Map<String, StoredAmount> stored = new LinkedHashMap<>();

    @Override
    public Optional<StoredAmount> find(String key) {
        return Optional.ofNullable(stored.get(key));
    }

    @Override
    public Map<String, StoredAmount> findAll() {
        return new LinkedHashMap<>(stored);
    }

    @Override
    public StoredAmount save(String key, BigDecimal amount, Instant updatedAt) {
        StoredAmount saved = new StoredAmount(amount, updatedAt);
        stored.put(key, saved);
        return saved;
    }

    public void clear() {
        stored.clear();
    }

    public int size() {
        return stored.size();
    }
}

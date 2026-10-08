package pl.tradeengine.domain.port.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Stored amounts of the manually maintained holdings, keyed by their stable configuration key. */
public interface ManualHoldingRepository {

    /** Amount and the moment it was set, or empty when nothing has been stored for this key yet. */
    Optional<StoredAmount> find(String key);

    /** Everything stored, so the listing endpoint needs one query rather than one per entry. */
    Map<String, StoredAmount> findAll();

    StoredAmount save(String key, BigDecimal amount, Instant updatedAt);

    record StoredAmount(BigDecimal amount, Instant updatedAt) {}
}

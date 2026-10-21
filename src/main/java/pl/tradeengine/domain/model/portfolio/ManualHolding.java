package pl.tradeengine.domain.model.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A holding whose amount a human maintains.
 *
 * <p>Split of responsibility: configuration says <em>which</em> manual positions exist — their
 * key, source, network, asset and how to price them — and the database says <em>how much</em>.
 * Without that split an amount set through the API would be lost on the next restart, and
 * without the configuration half a stray database row could invent a position nobody declared.
 *
 * @param updatedAt when the amount was last set, not when it was last valued
 */
public record ManualHolding(
        String key,
        String source,
        String network,
        String asset,
        int cmcId,
        BigDecimal amount,
        Instant updatedAt
) {}

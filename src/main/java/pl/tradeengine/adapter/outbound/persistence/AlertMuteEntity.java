package pl.tradeengine.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.domain.model.Timeframe;

import java.time.OffsetDateTime;

/**
 * One mute rule. The key is the three identifying columns joined into one string, because the
 * all-timeframes rule has a {@code NULL} timeframe, and a {@code UNIQUE} constraint treats NULLs
 * as distinct — it would happily store the same rule twice.
 */
@Entity
@Getter
@Setter
@Table(name = "alert_mute")
public class AlertMuteEntity {

    @Id
    @Column(name = "mute_key", nullable = false, length = 160)
    private String key;

    @Column(name = "strategy", nullable = false, length = 64)
    private String strategy;

    @Column(name = "symbol", nullable = false, length = 64)
    private String symbol;

    /** {@code NULL} = every timeframe. */
    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe", length = 8)
    private Timeframe timeframe;

    @Column(name = "muted_at", nullable = false)
    private OffsetDateTime mutedAt;

    /** E-mail from Cloudflare Access; {@code NULL} when the request bypassed it. */
    @Column(name = "muted_by", length = 254)
    private String mutedBy;
}

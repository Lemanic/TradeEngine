package pl.tradeengine.adapter.outbound.persistence.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The amount of one manual holding. The configuration key is the primary key — there is exactly
 * one current amount per declared position, and history lives in the snapshots instead.
 */
@Entity
@Getter
@Setter
@Table(name = "manual_holding")
public class ManualHoldingEntity {

    @Id
    @Column(name = "holding_key", nullable = false, length = 64)
    private String key;

    @Column(name = "amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

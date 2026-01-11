package pl.tradeengine.adapter.outbound.persistence;


import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;

@Entity
@Table(name = "market_bias")
@Getter
@Setter
public class BiasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private Timeframe timeframe;

    @Enumerated(EnumType.STRING)
    private BiasStatus status;

    private String lastReason;
    private ZonedDateTime updatedAt;

    public BiasEntity() {}

    public BiasEntity(String symbol, Timeframe timeframe, BiasStatus status, String lastReason, ZonedDateTime updatedAt) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.status = status;
        this.lastReason = lastReason;
        this.updatedAt = updatedAt;
    }
}

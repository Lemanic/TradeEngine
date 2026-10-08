package pl.tradeengine.adapter.outbound.persistence.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(
        name = "portfolio_position",
        indexes = @Index(name = "idx_portfolio_position_snapshot", columnList = "snapshot_id")
)
public class PortfolioPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PortfolioSnapshotEntity snapshot;

    @Column(name = "source", nullable = false, length = 64)
    private String source;

    @Column(name = "network", nullable = false, length = 64)
    private String network;

    @Column(name = "asset", nullable = false, length = 32)
    private String asset;

    @Column(name = "cmc_id", nullable = false)
    private int cmcId;

    @Column(name = "amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "price_usd", nullable = false, precision = 38, scale = 18)
    private BigDecimal priceUsd;

    @Column(name = "price_pln", nullable = false, precision = 38, scale = 18)
    private BigDecimal pricePln;

    @Column(name = "value_usd", nullable = false, precision = 38, scale = 8)
    private BigDecimal valueUsd;

    @Column(name = "value_pln", nullable = false, precision = 38, scale = 8)
    private BigDecimal valuePln;

    @Column(name = "stale", nullable = false)
    private boolean stale;
}

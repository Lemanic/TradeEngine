package pl.tradeengine.adapter.outbound.persistence.portfolio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code taken_at} is unique: one hour, one snapshot. A re-run for the same hour replaces the
 * stored row rather than adding a second one.
 *
 * <p>Timestamps are {@link OffsetDateTime} so Hibernate maps them to
 * {@code timestamp with time zone} on both PostgreSQL and H2 — the prod profile runs
 * {@code ddl-auto: validate} against the hand-run {@code sql/portfolio_schema.sql}.
 */
@Entity
@Getter
@Setter
@Table(name = "portfolio_snapshot")
public class PortfolioSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taken_at", nullable = false, unique = true)
    private OffsetDateTime takenAt;

    @Column(name = "total_usd", nullable = false, precision = 38, scale = 8)
    private BigDecimal totalUsd;

    @Column(name = "total_pln", nullable = false, precision = 38, scale = 8)
    private BigDecimal totalPln;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioPositionEntity> positions = new ArrayList<>();

    public void addPosition(PortfolioPositionEntity position) {
        position.setSnapshot(this);
        positions.add(position);
    }
}

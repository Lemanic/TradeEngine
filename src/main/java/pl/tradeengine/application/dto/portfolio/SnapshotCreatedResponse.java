package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Body of {@code POST /api/portfolio/snapshots}: the snapshot exactly as stored, in both
 * currencies and at full precision. This is an operator endpoint for checking that the balances
 * match the real wallets — it is deliberately not the UI contract's {@code summary} shape.
 */
@Schema(name = "SnapshotCreated")
public record SnapshotCreatedResponse(
        Instant takenAt,
        boolean complete,
        BigDecimal totalUsd,
        BigDecimal totalPln,
        List<Position> positions
) {

    /** Named apart from the contract's {@code Position} so the two never collide in the schema. */
    @Schema(name = "SnapshotPosition")
    public record Position(
            String source,
            String network,
            String asset,
            int cmcId,
            BigDecimal amount,
            BigDecimal priceUsd,
            BigDecimal pricePln,
            BigDecimal valueUsd,
            BigDecimal valuePln,
            boolean stale
    ) {}

    public static SnapshotCreatedResponse from(PortfolioSnapshot snapshot) {
        return new SnapshotCreatedResponse(
                snapshot.takenAt(),
                snapshot.complete(),
                snapshot.totalUsd(),
                snapshot.totalPln(),
                snapshot.positions().stream()
                        .map(position -> new Position(
                                position.source(),
                                position.network(),
                                position.asset(),
                                position.cmcId(),
                                position.amount(),
                                position.priceUsd(),
                                position.pricePln(),
                                position.valueUsd(),
                                position.valuePln(),
                                position.stale()))
                        .toList()
        );
    }
}

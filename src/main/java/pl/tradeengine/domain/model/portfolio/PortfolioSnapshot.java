package pl.tradeengine.domain.model.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The whole portfolio at one point in time. {@code takenAt} is always truncated to a full hour
 * in UTC — one hour, one snapshot.
 *
 * @param complete {@code false} when the snapshot did not cover every source.
 */
public record PortfolioSnapshot(
        Long id,
        Instant takenAt,
        BigDecimal totalUsd,
        BigDecimal totalPln,
        boolean complete,
        List<PortfolioPosition> positions
) {

    public BigDecimal total(FiatCurrency currency) {
        return currency == FiatCurrency.USD ? totalUsd : totalPln;
    }

    public SnapshotHeader header() {
        return new SnapshotHeader(takenAt, totalUsd, totalPln, complete);
    }
}

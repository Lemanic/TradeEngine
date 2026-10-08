package pl.tradeengine.domain.model.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A snapshot without its positions. History and the change/ATH calculations only need the
 * totals, so they are read through this projection and never load positions.
 */
public record SnapshotHeader(
        Instant takenAt,
        BigDecimal totalUsd,
        BigDecimal totalPln,
        boolean complete
) {

    public BigDecimal total(FiatCurrency currency) {
        return currency == FiatCurrency.USD ? totalUsd : totalPln;
    }
}

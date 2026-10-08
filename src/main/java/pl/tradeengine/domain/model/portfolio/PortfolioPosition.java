package pl.tradeengine.domain.model.portfolio;

import java.math.BigDecimal;

/**
 * One priced position inside a snapshot.
 *
 * @param stale {@code true} when the position was not refreshed in this snapshot: either the
 *              amount was carried forward from the previous snapshot because the source failed,
 *              or the price fell back to the last known one.
 */
public record PortfolioPosition(
        Long id,
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
) {

    public BigDecimal value(FiatCurrency currency) {
        return currency == FiatCurrency.USD ? valueUsd : valuePln;
    }

    public BigDecimal price(FiatCurrency currency) {
        return currency == FiatCurrency.USD ? priceUsd : pricePln;
    }
}

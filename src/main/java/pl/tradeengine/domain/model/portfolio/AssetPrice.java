package pl.tradeengine.domain.model.portfolio;

import java.math.BigDecimal;

/** Unit price of one asset in both supported currencies. */
public record AssetPrice(BigDecimal usd, BigDecimal pln) {

    public BigDecimal in(FiatCurrency currency) {
        return currency == FiatCurrency.USD ? usd : pln;
    }
}

package pl.tradeengine.domain.model.portfolio;

/**
 * The currencies a snapshot is valued in. Both are stored on every snapshot, so
 * history, change and ATH are computed per currency instead of by converting one
 * into the other (see docs/portfolio-api.md, decision 5).
 */
public enum FiatCurrency {
    USD,
    PLN
}

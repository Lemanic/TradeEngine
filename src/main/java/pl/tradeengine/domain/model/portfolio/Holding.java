package pl.tradeengine.domain.model.portfolio;

import java.math.BigDecimal;

/**
 * A raw, unpriced position as reported by a {@link pl.tradeengine.domain.port.portfolio.HoldingsSource}.
 *
 * @param source  display label of the source, e.g. {@code "Rabby"}. Several sources may share
 *                a label; their holdings are summed into one API source.
 * @param network network the asset sits on, e.g. {@code "ethereum"}. Shown verbatim by the UI.
 * @param asset   asset symbol, e.g. {@code "ETH"}.
 * @param cmcId   numeric CoinMarketCap id used to price the asset.
 * @param amount  amount held, in asset units.
 */
public record Holding(
        String source,
        String network,
        String asset,
        int cmcId,
        BigDecimal amount
) {}

package pl.tradeengine.domain.port.portfolio;

import pl.tradeengine.domain.model.portfolio.AssetPrice;

import java.util.Map;
import java.util.Set;

/** Prices in USD and PLN for a set of CoinMarketCap ids. */
public interface PriceProvider {

    /**
     * @return prices keyed by CoinMarketCap id. Ids the provider does not know about are
     *         absent from the map; the caller falls back to the last known price.
     * @throws PriceUnavailableException when the lookup failed as a whole, in either currency.
     */
    Map<Integer, AssetPrice> pricesFor(Set<Integer> cmcIds);
}

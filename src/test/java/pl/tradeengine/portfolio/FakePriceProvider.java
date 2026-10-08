package pl.tradeengine.portfolio;

import pl.tradeengine.domain.model.portfolio.AssetPrice;
import pl.tradeengine.domain.port.portfolio.PriceProvider;
import pl.tradeengine.domain.port.portfolio.PriceUnavailableException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Serves prices from a map; ids absent from the map are reported as unknown, like CMC does. */
public class FakePriceProvider implements PriceProvider {

    private final Map<Integer, AssetPrice> prices = new LinkedHashMap<>();
    private String failure;
    private Set<Integer> lastRequestedIds = Set.of();

    public FakePriceProvider with(int cmcId, String usd, String pln) {
        prices.put(cmcId, new AssetPrice(new BigDecimal(usd), new BigDecimal(pln)));
        return this;
    }

    public FakePriceProvider failingWith(String message) {
        this.failure = message;
        return this;
    }

    public Set<Integer> lastRequestedIds() {
        return lastRequestedIds;
    }

    @Override
    public Map<Integer, AssetPrice> pricesFor(Set<Integer> cmcIds) {
        lastRequestedIds = new LinkedHashSet<>(cmcIds);
        if (failure != null) {
            throw new PriceUnavailableException(failure);
        }
        Map<Integer, AssetPrice> answer = new LinkedHashMap<>();
        cmcIds.forEach(id -> {
            AssetPrice price = prices.get(id);
            if (price != null) {
                answer.put(id, price);
            }
        });
        return answer;
    }
}

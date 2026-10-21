package pl.tradeengine.adapter.outbound.hyperliquid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotBalance;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Capital held on Hyperliquid behind one wallet label, reported as a single network.
 *
 * <p>Three buckets land on the same network name, kept apart by asset:
 * <ul>
 *   <li><b>spot</b> — whitelisted token balances, priced through CoinMarketCap like any other
 *       holding. Unit's wrapped tokens are mapped to the asset they represent, which the API
 *       itself spells out in {@code fullName}: {@code UETH} is "Unit Ethereum" so it is priced
 *       as ETH, {@code HPENGU} is "Pudgy Penguins" so it is priced as PENGU.</li>
 *   <li><b>perp equity</b> — {@code accountValue}, which is USD after unrealised PnL rather than
 *       a token balance. It is reported as an amount of the configured dollar asset (USDC's
 *       CoinMarketCap id, whose price is ~1), so it shows up as its own row worth exactly that
 *       many dollars instead of being folded into the spot USDC balance.</li>
 *   <li><b>staking</b> — delegated HYPE, as a HYPE holding.</li>
 * </ul>
 *
 * <p>Equity on a leveraged account moves with the market and can be wiped out by liquidation, so
 * this position is expected to be the most volatile one in the portfolio.
 */
public class HyperliquidHoldingsSource implements HoldingsSource {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidHoldingsSource.class);

    /** One whitelisted spot token: how Hyperliquid names it, and what it actually is. */
    public record SpotToken(String coin, String asset, int cmcId) {}

    /** Asset used to express a USD amount, and the asset used for staked HYPE. */
    public record AssetRef(String asset, int cmcId) {}

    private final String label;
    private final String network;
    private final String baseUrl;
    private final String address;
    private final Map<String, SpotToken> spotWhitelist;
    private final AssetRef perps;
    private final AssetRef staking;
    private final HyperliquidClient client;

    public HyperliquidHoldingsSource(String label, String network, String baseUrl, String address,
                                     List<SpotToken> spotTokens, AssetRef perps, AssetRef staking,
                                     HyperliquidClient client) {
        this.label = label;
        this.network = network;
        this.baseUrl = baseUrl;
        this.address = address;
        this.spotWhitelist = spotTokens.stream()
                .collect(Collectors.toMap(SpotToken::coin, token -> token,
                        (left, right) -> left, LinkedHashMap::new));
        this.perps = perps;
        this.staking = staking;
        this.client = client;
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public Set<String> networks() {
        return Set.of(network);
    }

    @Override
    public List<Holding> fetchHoldings() {
        List<Holding> holdings = new ArrayList<>();
        holdings.addAll(spotHoldings());
        perpEquity().ifPresent(holdings::add);
        stakedHype().ifPresent(holdings::add);

        log.info("{}: read {} holding(s) on {}", label, holdings.size(), network);
        return holdings;
    }

    private List<Holding> spotHoldings() {
        List<SpotBalance> balances = client.spotState(baseUrl, address).balances();
        if (balances == null) {
            return List.of();
        }

        List<Holding> holdings = new ArrayList<>();
        Set<String> seenCoins = new HashSet<>();
        for (SpotBalance balance : balances) {
            if (balance.coin() == null || balance.total() == null) {
                continue;
            }
            SpotToken token = spotWhitelist.get(balance.coin());
            if (token == null) {
                continue;
            }
            if (!seenCoins.add(balance.coin())) {
                // Hyperliquid allows several tokens to share a ticker; if that ever happens for a
                // whitelisted one, pricing the second by the same name would be a guess.
                log.warn("{} on {}: '{}' appears more than once in spot balances (token ids differ)"
                        + " — only the first is counted", label, network, balance.coin());
                continue;
            }
            if (balance.total().signum() <= 0) {
                log.debug("{} on {}: no {} balance, skipping", label, network, balance.coin());
                continue;
            }
            holdings.add(new Holding(label, network, token.asset(), token.cmcId(), balance.total()));
        }
        return holdings;
    }

    private Optional<Holding> perpEquity() {
        var margin = client.perpState(baseUrl, address).marginSummary();
        BigDecimal accountValue = margin == null ? null : margin.accountValue();
        if (accountValue == null || accountValue.signum() <= 0) {
            log.debug("{} on {}: no perp equity, skipping", label, network);
            return Optional.empty();
        }
        return Optional.of(
                new Holding(label, network, perps.asset(), perps.cmcId(), accountValue));
    }

    private Optional<Holding> stakedHype() {
        BigDecimal delegated = client.stakingState(baseUrl, address).delegated();
        if (delegated == null || delegated.signum() <= 0) {
            log.debug("{} on {}: nothing staked, skipping", label, network);
            return Optional.empty();
        }
        return Optional.of(
                new Holding(label, network, staking.asset(), staking.cmcId(), delegated));
    }
}

package pl.tradeengine.adapter.outbound.coinbase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseAccountsResponse.Account;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Balances held on Coinbase.
 *
 * <p>Whitelisted currencies only, like every other source. That matters more here than on a
 * chain: an exchange account accumulates dust from fees and conversions — this wallet holds
 * {@code 7E-7} BTC and {@code 3.6E-9} ETH, together worth a few cents — and listing those as
 * positions would be noise, not information.
 *
 * <p>{@code available_balance} is used rather than adding {@code hold}: funds reserved against an
 * open order are still owned, but Coinbase reports them separately and the whitelist already
 * decides what counts. If an open order ever hides a meaningful amount, that is the place to look.
 */
public class CoinbaseHoldingsSource implements HoldingsSource {

    private static final Logger log = LoggerFactory.getLogger(CoinbaseHoldingsSource.class);

    /** One whitelisted currency: how Coinbase names it, and how to price it. */
    public record Currency(String code, String asset, int cmcId, BigDecimal minAmount) {}

    private final String label;
    private final String network;
    private final Map<String, Currency> whitelist;
    private final CoinbaseClient client;

    public CoinbaseHoldingsSource(String label, String network, List<Currency> currencies,
                                  CoinbaseClient client) {
        this.label = label;
        this.network = network;
        this.whitelist = currencies.stream().collect(Collectors.toMap(
                Currency::code, currency -> currency, (left, right) -> left, LinkedHashMap::new));
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

        for (Account account : client.accounts()) {
            Currency currency = account.currency() == null ? null : whitelist.get(account.currency());
            if (currency == null || account.availableBalance() == null) {
                continue;
            }
            BigDecimal amount = account.availableBalance().value();
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            if (amount.compareTo(currency.minAmount()) < 0) {
                log.debug("{}: {} balance {} is below the configured minimum {}, skipping",
                        label, currency.code(), amount, currency.minAmount());
                continue;
            }
            holdings.add(new Holding(label, network, currency.asset(), currency.cmcId(), amount));
        }

        log.info("{}: read {} whitelisted balance(s) on {}", label, holdings.size(), network);
        return holdings;
    }
}

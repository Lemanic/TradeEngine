package pl.tradeengine.adapter.outbound.manual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Amounts a human typed in, for holdings nothing can read automatically: a chain without a
 * working indexer, staking outside any API, a cold wallet.
 *
 * <p>Configuration decides <em>which</em> positions exist; the amount is looked up per snapshot
 * through {@link AmountLookup}, which reads the database. That split is what makes
 * {@code PUT /api/portfolio/manual-holdings/{key}} survive a restart — were the amount captured
 * here at startup, the endpoint would change one snapshot and then be forgotten.
 *
 * <p>Pricing goes through {@code PriceProvider} exactly like a holding read from a chain, so the
 * value and the PLN conversion stay consistent with the rest of the portfolio, and the position is
 * <b>not</b> marked {@code stale} — per docs/portfolio-api.md it is not stale merely for being
 * manual, as long as its amount and price enter the snapshot normally.
 *
 * <p>This source cannot fail: there is nothing to call. So it never triggers carry-forward and
 * never makes a snapshot incomplete. The flip side is the only real weakness of manual entries —
 * <b>the number is true only until the holding changes.</b> Move the coins and forget to send the
 * PUT, and the portfolio keeps reporting them with full confidence. Reading a chain is always
 * preferable where it is possible.
 */
public class ManualHoldingsSource implements HoldingsSource {

    private static final Logger log = LoggerFactory.getLogger(ManualHoldingsSource.class);

    /** Reads the current amount for a key. Backed by {@code ManualHoldingService}. */
    public interface AmountLookup extends Function<String, BigDecimal> {}

    /** One declared entry. {@code key} is the stable id the PUT endpoint addresses. */
    public record Entry(String key, String network, String asset, int cmcId) {}

    private final String label;
    private final List<Entry> entries;
    private final AmountLookup amounts;

    public ManualHoldingsSource(String label, List<Entry> entries, AmountLookup amounts) {
        this.label = label;
        this.entries = List.copyOf(entries);
        this.amounts = amounts;
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public Set<String> networks() {
        return entries.stream()
                .map(Entry::network)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<Holding> fetchHoldings() {
        List<Holding> holdings = entries.stream()
                .map(entry -> new Holding(label, entry.network(), entry.asset(), entry.cmcId(),
                        amount(entry)))
                .filter(holding -> {
                    if (holding.amount().signum() > 0) {
                        return true;
                    }
                    // Zero is a legitimate value meaning "nothing here right now"; the entry stays
                    // declared and comes back when the amount grows again.
                    log.debug("{}: manual entry '{}' is zero, skipping", label, holding.asset());
                    return false;
                })
                .toList();

        if (!holdings.isEmpty()) {
            log.info("{}: {} manual holding(s): {}", label, holdings.size(),
                    holdings.stream().map(Holding::asset).toList());
        }
        return holdings;
    }

    private BigDecimal amount(Entry entry) {
        BigDecimal amount = amounts.apply(entry.key());
        return amount == null ? BigDecimal.ZERO : amount;
    }
}

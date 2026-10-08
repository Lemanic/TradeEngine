package pl.tradeengine.domain.port.portfolio;

import pl.tradeengine.domain.model.portfolio.Holding;

import java.util.List;
import java.util.Set;

/**
 * One place amounts are read from — a wallet label on a set of networks, an exchange, an xpub.
 *
 * <p>Several instances may return the same {@link #name()}: a label such as {@code "Trezor"} can
 * span an EVM address and (later) a BCH xpub. {@link #networks()} is what keeps those instances
 * apart — when a source fails, {@code PortfolioSnapshotService} carries forward exactly the
 * previous positions whose source matches {@link #name()} and whose network is in
 * {@link #networks()}, so a failure of one half of a label does not touch the other half.
 */
public interface HoldingsSource {

    /** Display label, used verbatim as {@code positions[].source} in the API. */
    String name();

    /** Networks this instance is responsible for. */
    Set<String> networks();

    /**
     * Reads current amounts. Implementations throw when the source cannot be reached; the
     * caller turns that into carried-forward, stale positions and an incomplete snapshot.
     */
    List<Holding> fetchHoldings();
}

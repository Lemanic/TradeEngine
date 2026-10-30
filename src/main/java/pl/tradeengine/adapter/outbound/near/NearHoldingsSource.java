package pl.tradeengine.adapter.outbound.near;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * NEAR held by one account: everything staked with its validators plus the liquid balance.
 *
 * <p>Read from the chain rather than entered by hand, which is the whole point of this source.
 * NEAR's staking pool compounds each epoch's rewards into the delegator's balance, so a manual
 * amount would understate the position from the moment it was typed — the way {@code TAO staking}
 * and {@code TRB staking} do, except here the number is readable.
 *
 * <p>One {@link Holding}, not two: positions aggregate on (source, network, asset, cmcId), so a
 * staked and a liquid row under the same label and network would be summed anyway.
 *
 * <p>A pool that cannot be read aborts the whole source instead of contributing zero. A partial
 * sum would quietly understate the portfolio and look like a real drop on the chart, whereas an
 * exception hands the job to carry-forward: the previous amount, priced today, with
 * {@code stale=true} and the snapshot marked incomplete.
 */
public class NearHoldingsSource implements HoldingsSource {

    private static final Logger log = LoggerFactory.getLogger(NearHoldingsSource.class);

    private final String label;
    private final String network;
    private final String rpcUrl;
    private final String apiBaseUrl;
    private final String accountId;
    private final String asset;
    private final int cmcId;
    private final NearClient client;

    public NearHoldingsSource(String label, String network, String rpcUrl, String apiBaseUrl,
                              String accountId, String asset, int cmcId, NearClient client) {
        this.label = label;
        this.network = network;
        this.rpcUrl = rpcUrl;
        this.apiBaseUrl = apiBaseUrl;
        this.accountId = accountId;
        this.asset = asset;
        this.cmcId = cmcId;
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
        List<String> pools = client.stakingPools(apiBaseUrl, accountId);

        BigDecimal staked = BigDecimal.ZERO;
        int funded = 0;
        for (String pool : pools) {
            BigDecimal balance = client.stakedBalance(rpcUrl, pool, accountId);
            // Discovery also reports pools the account has left; those answer with a zero.
            if (balance.signum() <= 0) {
                log.debug("{}: pool {} holds nothing, skipping", label, pool);
                continue;
            }
            staked = staked.add(balance);
            funded++;
        }

        BigDecimal total = staked.add(client.liquidBalance(rpcUrl, accountId));
        if (total.signum() <= 0) {
            log.debug("{} on {}: no {} balance, skipping", label, network, asset);
            return List.of();
        }

        log.info("{}: read {} balance on {} — staked in {} of {} pool(s), plus the liquid balance",
                label, asset, network, funded, pools.size());
        return List.of(new Holding(label, network, asset, cmcId, total));
    }
}

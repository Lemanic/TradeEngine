package pl.tradeengine.adapter.outbound.evm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All EVM holdings behind one wallet label. A label may cover several (address, network) pairs —
 * they are read in turn and their amounts end up summed per (source, network, asset) by
 * {@code PortfolioSnapshotService}.
 *
 * <p>Only whitelisted tokens are read; a zero balance is dropped instead of becoming a
 * zero-valued position.
 */
public class EvmHoldingsSource implements HoldingsSource {

    private static final Logger log = LoggerFactory.getLogger(EvmHoldingsSource.class);

    /** One address on one network, with everything needed to read it. */
    public record Target(
            String address,
            String network,
            String rpcUrl,
            String nativeAsset,
            int nativeCmcId,
            List<Token> tokens
    ) {}

    /** A whitelisted ERC-20 on that network. */
    public record Token(String asset, String contract, int decimals, int cmcId) {}

    private final String label;
    private final List<Target> targets;
    private final EvmBalanceClient client;

    public EvmHoldingsSource(String label, List<Target> targets, EvmBalanceClient client) {
        this.label = label;
        this.targets = List.copyOf(targets);
        this.client = client;
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public Set<String> networks() {
        return targets.stream()
                .map(Target::network)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<Holding> fetchHoldings() {
        List<Holding> holdings = new ArrayList<>();

        for (Target target : targets) {
            BigDecimal nativeAmount = client.nativeBalance(target.rpcUrl(), target.address());
            if (nativeAmount.signum() > 0) {
                holdings.add(new Holding(label, target.network(), target.nativeAsset(),
                        target.nativeCmcId(), nativeAmount));
            } else {
                log.debug("{} on {}: no {} balance, skipping", label, target.network(), target.nativeAsset());
            }

            for (Token token : target.tokens()) {
                BigDecimal amount = client.tokenBalance(
                        target.rpcUrl(), token.contract(), target.address(), token.decimals());
                if (amount.signum() > 0) {
                    holdings.add(new Holding(label, target.network(), token.asset(), token.cmcId(), amount));
                } else {
                    log.debug("{} on {}: no {} balance, skipping", label, target.network(), token.asset());
                }
            }
        }

        log.info("{}: read {} non-zero holding(s) from {} address/network pair(s)",
                label, holdings.size(), targets.size());
        return holdings;
    }
}

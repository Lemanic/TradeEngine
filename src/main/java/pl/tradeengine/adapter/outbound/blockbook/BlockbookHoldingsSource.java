package pl.tradeengine.adapter.outbound.blockbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * One UTXO-chain balance behind a wallet label, read from an xpub.
 *
 * <p>Shares its {@link #name()} with the EVM source of the same wallet — "Trezor" covers both an
 * EVM address on bsc and this xpub on bitcoin — and the two stay apart through
 * {@link #networks()}, so a Blockbook outage carries forward only the Bitcoin position.
 */
public class BlockbookHoldingsSource implements HoldingsSource {

    private static final Logger log = LoggerFactory.getLogger(BlockbookHoldingsSource.class);

    private final String label;
    private final String network;
    private final String baseUrl;
    private final String xpub;
    private final String asset;
    private final int cmcId;
    private final int decimals;
    private final BlockbookClient client;

    public BlockbookHoldingsSource(String label, String network, String baseUrl, String xpub,
                                   String asset, int cmcId, int decimals, BlockbookClient client) {
        this.label = label;
        this.network = network;
        this.baseUrl = baseUrl;
        this.xpub = xpub;
        this.asset = asset;
        this.cmcId = cmcId;
        this.decimals = decimals;
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
        BigDecimal amount = client.balance(network, baseUrl, xpub, decimals);

        if (amount.signum() <= 0) {
            log.debug("{} on {}: no {} balance, skipping", label, network, asset);
            return List.of();
        }

        log.info("{}: read {} balance on {}", label, asset, network);
        return List.of(new Holding(label, network, asset, cmcId, amount));
    }
}

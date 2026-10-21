package pl.tradeengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import pl.tradeengine.adapter.outbound.blockbook.BlockbookClient;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseClient;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseHoldingsSource;
import pl.tradeengine.adapter.outbound.blockbook.BlockbookHoldingsSource;
import pl.tradeengine.adapter.outbound.evm.EvmBalanceClient;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidClient;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidHoldingsSource;
import pl.tradeengine.adapter.outbound.manual.ManualHoldingsSource;
import pl.tradeengine.application.service.portfolio.ManualHoldingService;
import pl.tradeengine.adapter.outbound.evm.EvmHoldingsSource;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns configuration into holdings sources: one per wallet label for EVM, one per xpub for the
 * UTXO chains, plus any {@code HoldingsSource} registered as a bean.
 *
 * <p>A label may end up with several sources — "Trezor" covers an EVM address on bsc and an xpub
 * on bitcoin — and that is intentional: their positions are summed into one API source, while
 * {@code networks()} keeps a failure of one from touching the other.
 *
 * <p>Misconfiguration never stops the application: a wallet without an address, an unknown
 * network or an incomplete token entry is logged and skipped. The same (address, network) pair
 * configured twice is skipped as well — counting it twice would inflate the portfolio.
 */
@Component
public class HoldingsSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(HoldingsSourceRegistry.class);

    private final List<HoldingsSource> sources;

    public HoldingsSourceRegistry(PortfolioProperties properties,
                                  EvmBalanceClient evmBalanceClient,
                                  BlockbookClient blockbookClient,
                                  HyperliquidClient hyperliquidClient,
                                  ObjectProvider<CoinbaseClient> coinbaseClient,
                                  ManualHoldingService manualHoldingService,
                                  ObjectProvider<HoldingsSource> beanSources) {
        List<HoldingsSource> all = new ArrayList<>(evmSources(properties.getEvm(), evmBalanceClient));
        all.addAll(xpubSources(properties.getBlockbook(), blockbookClient));
        all.addAll(hyperliquidSources(properties.getHyperliquid(), hyperliquidClient));
        all.addAll(manualSources(manualHoldingService));
        all.addAll(coinbaseSources(properties.getCoinbase(), coinbaseClient.getIfAvailable()));
        beanSources.orderedStream().forEach(all::add);
        this.sources = List.copyOf(all);

        if (this.sources.isEmpty()) {
            log.warn("No portfolio holdings source configured — snapshots will be skipped");
        } else {
            this.sources.forEach(source ->
                    log.info("Portfolio source '{}' covering {}", source.name(), source.networks()));
            warnOnOverlappingCoverage(this.sources);
        }
    }

    /**
     * Two sources covering the same label <em>and</em> the same network would have their amounts
     * summed, because positions aggregate on (source, network, asset, cmcId). That is silent
     * double counting of real money, so it is worth shouting about at startup.
     *
     * <p>The case this guards against concretely: a manual BCH amount left in place after the
     * xpub source starts working again.
     */
    private static void warnOnOverlappingCoverage(List<HoldingsSource> sources) {
        Map<String, List<HoldingsSource>> byPair = new LinkedHashMap<>();
        for (HoldingsSource source : sources) {
            for (String network : source.networks()) {
                byPair.computeIfAbsent(source.name() + " / " + network, key -> new ArrayList<>())
                        .add(source);
            }
        }
        byPair.forEach((pair, owners) -> {
            if (owners.size() > 1) {
                log.warn("TWO SOURCES COVER '{}': {}. Amounts for the same asset there will be "
                                + "SUMMED, which double counts the holding. Zero out or remove one "
                                + "of them.",
                        pair, owners.stream().map(source -> source.getClass().getSimpleName()).toList());
            }
        });
    }

    public List<HoldingsSource> all() {
        return sources;
    }

    private static List<HoldingsSource> evmSources(PortfolioProperties.Evm evm, EvmBalanceClient client) {
        Map<String, List<EvmHoldingsSource.Target>> byLabel = new LinkedHashMap<>();
        Set<String> seenPairs = new HashSet<>();

        for (PortfolioProperties.Wallet wallet : evm.getWallets()) {
            String label = wallet.getLabel();
            if (isBlank(label)) {
                log.warn("portfolio.evm.wallets has an entry without a label — skipped");
                continue;
            }
            if (isBlank(wallet.getAddress())) {
                log.warn("Wallet '{}' has no address configured — skipped (is its env var set?)", label);
                continue;
            }

            for (String network : wallet.getNetworks()) {
                PortfolioProperties.Network config = evm.getNetworks().get(network);
                if (config == null) {
                    log.warn("Wallet '{}' points at network '{}', which is not under portfolio.evm.networks — skipped",
                            label, network);
                    continue;
                }
                if (isBlank(config.getRpcUrl())) {
                    log.warn("Network '{}' has no rpc-url — skipped for wallet '{}'", network, label);
                    continue;
                }
                PortfolioProperties.NativeAsset nativeAsset = config.getNativeAsset();
                if (nativeAsset == null || isBlank(nativeAsset.getAsset()) || nativeAsset.getCmcId() == null) {
                    log.warn("Network '{}' has no complete native-asset (asset + cmc-id) — skipped for wallet '{}'",
                            network, label);
                    continue;
                }
                if (!seenPairs.add(wallet.getAddress().toLowerCase() + "@" + network)) {
                    log.warn("Address {} on {} is configured more than once — the duplicate is skipped "
                                    + "so its balances are not counted twice",
                            wallet.getAddress(), network);
                    continue;
                }

                byLabel.computeIfAbsent(label, key -> new ArrayList<>()).add(new EvmHoldingsSource.Target(
                        wallet.getAddress(),
                        network,
                        config.getRpcUrl(),
                        nativeAsset.getAsset(),
                        nativeAsset.getCmcId(),
                        tokens(network, config)
                ));
            }
        }

        return byLabel.entrySet().stream()
                .map(entry -> (HoldingsSource) new EvmHoldingsSource(entry.getKey(), entry.getValue(), client))
                .toList();
    }

    private static List<HoldingsSource> xpubSources(PortfolioProperties.Blockbook blockbook,
                                                    BlockbookClient client) {
        List<HoldingsSource> sources = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (PortfolioProperties.XpubWallet wallet : blockbook.getWallets()) {
            String label = wallet.getLabel();
            if (isBlank(label)) {
                log.warn("portfolio.blockbook.wallets has an entry without a label — skipped");
                continue;
            }
            if (isBlank(wallet.getXpub())) {
                log.warn("Wallet '{}' has no xpub configured — skipped (is its env var set?)", label);
                continue;
            }
            if (isBlank(wallet.getNetwork())) {
                log.warn("Xpub of '{}' has no network — skipped", label);
                continue;
            }

            PortfolioProperties.BlockbookNetwork config = blockbook.getNetworks().get(wallet.getNetwork());
            if (config == null) {
                log.warn("Wallet '{}' points at network '{}', which is not under "
                        + "portfolio.blockbook.networks — skipped", label, wallet.getNetwork());
                continue;
            }
            if (isBlank(config.getBaseUrl()) || isBlank(config.getAsset())
                    || config.getCmcId() == null || config.getDecimals() == null) {
                log.warn("Network '{}' needs base-url, asset, cmc-id and decimals — "
                        + "skipped for wallet '{}'", wallet.getNetwork(), label);
                continue;
            }
            // The xpub never reaches the log, so the duplicate key is hashed rather than printed.
            if (!seenPairs.add(wallet.getXpub() + "@" + wallet.getNetwork())) {
                log.warn("The same xpub is configured twice on {} for '{}' — the duplicate is "
                        + "skipped so its balance is not counted twice", wallet.getNetwork(), label);
                continue;
            }

            sources.add(new BlockbookHoldingsSource(
                    label,
                    wallet.getNetwork(),
                    config.getBaseUrl(),
                    wallet.getXpub(),
                    config.getAsset(),
                    config.getCmcId(),
                    config.getDecimals(),
                    client));
        }
        return sources;
    }

    private static List<HoldingsSource> hyperliquidSources(PortfolioProperties.Hyperliquid config,
                                                           HyperliquidClient client) {
        if (config.getWallets().isEmpty()) {
            return List.of();
        }
        if (isBlank(config.getBaseUrl()) || isBlank(config.getNetwork())) {
            log.warn("portfolio.hyperliquid needs base-url and network — skipped");
            return List.of();
        }

        HyperliquidHoldingsSource.AssetRef perps = assetRef(config.getPerps(), "perps");
        HyperliquidHoldingsSource.AssetRef staking = assetRef(config.getStaking(), "staking");
        List<HyperliquidHoldingsSource.SpotToken> spotTokens = new ArrayList<>();
        for (PortfolioProperties.SpotToken token : config.getSpotTokens()) {
            if (isBlank(token.getCoin()) || isBlank(token.getAsset()) || token.getCmcId() == null) {
                log.warn("portfolio.hyperliquid.spot-tokens entry needs coin, asset and cmc-id — skipped");
                continue;
            }
            spotTokens.add(new HyperliquidHoldingsSource.SpotToken(
                    token.getCoin(), token.getAsset(), token.getCmcId()));
        }

        List<HoldingsSource> sources = new ArrayList<>();
        Set<String> seenAddresses = new HashSet<>();
        for (PortfolioProperties.HyperliquidWallet wallet : config.getWallets()) {
            if (isBlank(wallet.getLabel())) {
                log.warn("portfolio.hyperliquid.wallets has an entry without a label — skipped");
                continue;
            }
            if (isBlank(wallet.getAddress())) {
                log.warn("Hyperliquid wallet '{}' has no address configured — skipped "
                        + "(is its env var set?)", wallet.getLabel());
                continue;
            }
            if (!seenAddresses.add(wallet.getAddress().toLowerCase())) {
                log.warn("Address {} is configured twice for Hyperliquid — the duplicate is "
                        + "skipped so its balances are not counted twice", wallet.getAddress());
                continue;
            }
            sources.add(new HyperliquidHoldingsSource(
                    wallet.getLabel(), config.getNetwork(), config.getBaseUrl(),
                    wallet.getAddress(), spotTokens, perps, staking, client));
        }
        return sources;
    }

    private static List<HoldingsSource> coinbaseSources(PortfolioProperties.Coinbase config,
                                                        CoinbaseClient client) {
        if (client == null) {
            log.warn("Coinbase has no API key configured — skipped (set its env vars to enable)");
            return List.of();
        }
        if (isBlank(config.getLabel()) || isBlank(config.getNetwork())) {
            log.warn("portfolio.coinbase needs label and network — skipped");
            return List.of();
        }

        List<CoinbaseHoldingsSource.Currency> currencies = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (PortfolioProperties.CoinbaseCurrency currency : config.getCurrencies()) {
            if (isBlank(currency.getCode()) || isBlank(currency.getAsset())
                    || currency.getCmcId() == null) {
                log.warn("portfolio.coinbase.currencies entry needs code, asset and cmc-id — skipped");
                continue;
            }
            if (!seenCodes.add(currency.getCode())) {
                log.warn("Coinbase currency '{}' is listed twice — the duplicate is skipped",
                        currency.getCode());
                continue;
            }
            BigDecimal minAmount = currency.getMinAmount() == null
                    ? BigDecimal.ZERO : currency.getMinAmount();
            currencies.add(new CoinbaseHoldingsSource.Currency(
                    currency.getCode(), currency.getAsset(), currency.getCmcId(), minAmount));
        }
        if (currencies.isEmpty()) {
            log.warn("Coinbase has no whitelisted currencies — skipped");
            return List.of();
        }

        return List.of(new CoinbaseHoldingsSource(
                config.getLabel(), config.getNetwork(), currencies, client));
    }

    /**
     * Jedno zrodlo na etykiete, zeby reczne pozycje dolaczaly do wlasciwego kawalka portfela.
     *
     * <p>Lista pozycji pochodzi z {@link ManualHoldingService}, nie z konfiguracji bezposrednio:
     * ilosc kazdej z nich jest odczytywana z bazy przy kazdym snapshocie, bo moze ja zmienic
     * {@code PUT /api/portfolio/manual-holdings/{key}}.
     */
    private static List<HoldingsSource> manualSources(ManualHoldingService service) {
        Map<String, List<ManualHoldingsSource.Entry>> byLabel = new LinkedHashMap<>();
        for (ManualHoldingService.Definition definition : service.definitions()) {
            byLabel.computeIfAbsent(definition.source(), key -> new ArrayList<>())
                    .add(new ManualHoldingsSource.Entry(definition.key(), definition.network(),
                            definition.asset(), definition.cmcId()));
        }
        return byLabel.entrySet().stream()
                .map(entry -> (HoldingsSource) new ManualHoldingsSource(
                        entry.getKey(), entry.getValue(), service::amountFor))
                .toList();
    }

    private static HyperliquidHoldingsSource.AssetRef assetRef(PortfolioProperties.AssetRef ref,
                                                               String what) {
        if (ref == null || isBlank(ref.getAsset()) || ref.getCmcId() == null) {
            throw new IllegalStateException(
                    "portfolio.hyperliquid." + what + " needs asset and cmc-id");
        }
        return new HyperliquidHoldingsSource.AssetRef(ref.getAsset(), ref.getCmcId());
    }

    private static List<EvmHoldingsSource.Token> tokens(String network, PortfolioProperties.Network config) {
        List<EvmHoldingsSource.Token> tokens = new ArrayList<>();
        for (PortfolioProperties.Token token : config.getTokens()) {
            if (isBlank(token.getAsset()) || isBlank(token.getContract())
                    || token.getDecimals() == null || token.getCmcId() == null) {
                log.warn("Token entry on '{}' is missing asset, contract, decimals or cmc-id — skipped", network);
                continue;
            }
            tokens.add(new EvmHoldingsSource.Token(
                    token.getAsset(), token.getContract(), token.getDecimals(), token.getCmcId()));
        }
        return tokens;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

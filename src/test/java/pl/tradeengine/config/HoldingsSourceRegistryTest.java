package pl.tradeengine.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pl.tradeengine.adapter.outbound.blockbook.BlockbookClient;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseClient;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidClient;
import pl.tradeengine.adapter.outbound.evm.EvmBalanceClient;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import pl.tradeengine.application.service.portfolio.InMemoryManualHoldingRepository;
import pl.tradeengine.application.service.portfolio.ManualHoldingService;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HoldingsSourceRegistryTest {

    private static final String ETH_RPC = "https://rpc.example/ethereum";
    private static final String ARB_RPC = "https://rpc.example/arbitrum";
    private static final String ADDRESS = "0xAbC0000000000000000000000000000000000001";

    private static final String BTC_URL = "https://btc1.trezor.test";
    private static final String XPUB = "zpubTESTONLY0000000000000000000000000000000000000000000000";

    private MockRestServiceServer server;
    private EvmBalanceClient client;
    private BlockbookClient blockbookClient;
    private HyperliquidClient hyperliquidClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new EvmBalanceClient(builder.build());
        blockbookClient = new BlockbookClient(builder.build(), "tradeengine/1.0");
        hyperliquidClient = new HyperliquidClient(builder.build());
    }

    /**
     * Rejestr bierze reczne pozycje z serwisu, a ten powstaje tak samo jak w aplikacji — przez
     * fabryke z {@link PortfolioConfig}, zeby walidacja wpisow byla ta sama.
     */
    private static ManualHoldingService manualHoldings(PortfolioProperties properties) {
        return new PortfolioConfig().manualHoldingService(properties,
                new InMemoryManualHoldingRepository(), Clock.systemUTC());
    }

    private static PortfolioProperties.Network network(String rpcUrl, String asset, int cmcId) {
        PortfolioProperties.NativeAsset nativeAsset = new PortfolioProperties.NativeAsset();
        nativeAsset.setAsset(asset);
        nativeAsset.setCmcId(cmcId);

        PortfolioProperties.Network network = new PortfolioProperties.Network();
        network.setRpcUrl(rpcUrl);
        network.setNativeAsset(nativeAsset);
        return network;
    }

    private static PortfolioProperties.Wallet wallet(String label, String address, String... networks) {
        PortfolioProperties.Wallet wallet = new PortfolioProperties.Wallet();
        wallet.setLabel(label);
        wallet.setAddress(address);
        wallet.setNetworks(List.of(networks));
        return wallet;
    }

    private PortfolioProperties properties(List<PortfolioProperties.Wallet> wallets) {
        PortfolioProperties properties = new PortfolioProperties();
        properties.getEvm().setNetworks(Map.of(
                "ethereum", network(ETH_RPC, "ETH", 1027),
                "arbitrum", network(ARB_RPC, "ETH", 1027)));
        properties.getEvm().setWallets(wallets);
        return properties;
    }

    private List<HoldingsSource> sources(List<PortfolioProperties.Wallet> wallets) {
        return sources(wallets, List.of());
    }

    private List<HoldingsSource> sources(List<PortfolioProperties.Wallet> wallets,
                                         List<PortfolioProperties.XpubWallet> xpubWallets) {
        PortfolioProperties properties = properties(wallets);

        PortfolioProperties.BlockbookNetwork bitcoin = new PortfolioProperties.BlockbookNetwork();
        bitcoin.setBaseUrl(BTC_URL);
        bitcoin.setAsset("BTC");
        bitcoin.setCmcId(1);
        bitcoin.setDecimals(8);
        properties.getBlockbook().setNetworks(Map.of("bitcoin", bitcoin));
        properties.getBlockbook().setWallets(xpubWallets);

        return new HoldingsSourceRegistry(properties, client, blockbookClient, hyperliquidClient,
                new NoCoinbase(), manualHoldings(properties), new NoBeanSources()).all();
    }

    private static PortfolioProperties.XpubWallet xpubWallet(String label, String xpub, String network) {
        PortfolioProperties.XpubWallet wallet = new PortfolioProperties.XpubWallet();
        wallet.setLabel(label);
        wallet.setXpub(xpub);
        wallet.setNetwork(network);
        return wallet;
    }

    @Test
    void buildsOneSourcePerLabelCoveringAllItsNetworks() {
        List<HoldingsSource> sources = sources(List.of(wallet("Rabby", ADDRESS, "ethereum", "arbitrum")));

        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.name()).isEqualTo("Rabby");
            assertThat(source.networks()).containsExactlyInAnyOrder("ethereum", "arbitrum");
        });
    }

    @Test
    void mergesSeveralWalletEntriesSharingALabelIntoOneSource() {
        String second = "0xAbC0000000000000000000000000000000000002";
        List<HoldingsSource> sources = sources(List.of(
                wallet("Rabby", ADDRESS, "ethereum"),
                wallet("Rabby", second, "arbitrum")));

        assertThat(sources).singleElement().satisfies(source ->
                assertThat(source.networks()).containsExactlyInAnyOrder("ethereum", "arbitrum"));
    }

    @Test
    void skipsAWalletWithoutAnAddressSoAMissingEnvVarCannotStopStartup() {
        assertThat(sources(List.of(wallet("Rabby", "", "ethereum")))).isEmpty();
        assertThat(sources(List.of(wallet("Rabby", null, "ethereum")))).isEmpty();
    }

    @Test
    void skipsAnUnknownNetwork() {
        assertThat(sources(List.of(wallet("Trezor", ADDRESS, "bsc")))).isEmpty();
    }

    @Test
    void readsADuplicatedAddressAndNetworkPairOnlyOnce() {
        server.expect(once(), requestTo(ETH_RPC))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x21720a3b34b10000\"}",
                        MediaType.APPLICATION_JSON));

        List<HoldingsSource> sources = sources(List.of(
                wallet("Rabby", ADDRESS, "ethereum"),
                wallet("Rabby", ADDRESS.toLowerCase(), "ethereum")));

        List<Holding> holdings = sources.getFirst().fetchHoldings();

        assertThat(holdings).singleElement()
                .satisfies(holding -> assertThat(holding.amount()).isEqualByComparingTo("2.41"));
        server.verify();
    }

    @Test
    void splitsALabelAcrossItsEvmAndXpubSources() {
        List<HoldingsSource> sources = sources(
                List.of(wallet("Trezor", ADDRESS, "ethereum")),
                List.of(xpubWallet("Trezor", XPUB, "bitcoin")));

        assertThat(sources).hasSize(2);
        assertThat(sources).allSatisfy(source -> assertThat(source.name()).isEqualTo("Trezor"));
        assertThat(sources).flatExtracting(HoldingsSource::networks)
                .containsExactlyInAnyOrder("ethereum", "bitcoin");
    }

    @Test
    void readsTheXpubBalanceThroughTheRegisteredSource() {
        server.expect(once(), requestTo(BTC_URL + "/api/v2/xpub/" + XPUB + "?details=basic"))
                .andRespond(withSuccess("{\"balance\":\"804707\"}", MediaType.APPLICATION_JSON));

        List<HoldingsSource> sources = sources(List.of(), List.of(xpubWallet("Trezor", XPUB, "bitcoin")));

        assertThat(sources.getFirst().fetchHoldings()).singleElement()
                .satisfies(holding -> assertThat(holding.amount()).isEqualByComparingTo("0.00804707"));
        server.verify();
    }

    @Test
    void skipsAnXpubWalletWithoutAKey() {
        assertThat(sources(List.of(), List.of(xpubWallet("Trezor", "", "bitcoin")))).isEmpty();
        assertThat(sources(List.of(), List.of(xpubWallet("Trezor", null, "bitcoin")))).isEmpty();
    }

    @Test
    void skipsAnXpubOnAnUnknownNetwork() {
        assertThat(sources(List.of(), List.of(xpubWallet("Trezor", XPUB, "litecoin")))).isEmpty();
    }

    @Test
    void readsADuplicatedXpubOnlyOnce() {
        List<HoldingsSource> sources = sources(List.of(),
                List.of(xpubWallet("Trezor", XPUB, "bitcoin"), xpubWallet("Trezor", XPUB, "bitcoin")));

        assertThat(sources).hasSize(1);
    }

    private static PortfolioProperties hyperliquidProperties(String label, String address) {
        PortfolioProperties properties = new PortfolioProperties();
        PortfolioProperties.Hyperliquid hl = properties.getHyperliquid();
        PortfolioProperties.HyperliquidWallet wallet = new PortfolioProperties.HyperliquidWallet();
        wallet.setLabel(label);
        wallet.setAddress(address);
        hl.setWallets(List.of(wallet));
        PortfolioProperties.SpotToken usdc = new PortfolioProperties.SpotToken();
        usdc.setCoin("USDC"); usdc.setAsset("USDC"); usdc.setCmcId(3408);
        hl.setSpotTokens(List.of(usdc));
        hl.getPerps().setAsset("USD"); hl.getPerps().setCmcId(3408);
        hl.getStaking().setAsset("HYPE"); hl.getStaking().setCmcId(32196);
        return properties;
    }

    private List<HoldingsSource> hyperliquidSources(PortfolioProperties properties) {
        return new HoldingsSourceRegistry(properties, client, blockbookClient, hyperliquidClient,
                new NoCoinbase(), manualHoldings(properties), new NoBeanSources()).all();
    }

    @Test
    void buildsAHyperliquidSourceOnOneNetwork() {
        List<HoldingsSource> sources = hyperliquidSources(hyperliquidProperties("Rabby", ADDRESS));

        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.name()).isEqualTo("Rabby");
            assertThat(source.networks()).containsExactly("hyperliquid");
        });
    }

    @Test
    void skipsHyperliquidWithoutAnAddress() {
        assertThat(hyperliquidSources(hyperliquidProperties("Rabby", ""))).isEmpty();
        assertThat(hyperliquidSources(hyperliquidProperties("Rabby", null))).isEmpty();
    }

    @Test
    void buildsNoHyperliquidSourceWhenNoWalletIsConfigured() {
        assertThat(hyperliquidSources(new PortfolioProperties())).isEmpty();
    }

    @Test
    void buildsManualSourcesGroupedByLabel() {
        PortfolioProperties properties = new PortfolioProperties();
        properties.getManual().setHoldings(List.of(
                manualHolding("trezor-bch", "Trezor", "bitcoin-cash", "BCH", 1831, "21.5"),
                manualHolding("tao", "TAO staking", "bittensor", "TAO", 22974, "27.78")));

        List<HoldingsSource> sources = new HoldingsSourceRegistry(properties, client,
                blockbookClient, hyperliquidClient, new NoCoinbase(), manualHoldings(properties),
                new NoBeanSources()).all();

        assertThat(sources).extracting(HoldingsSource::name)
                .containsExactlyInAnyOrder("Trezor", "TAO staking");
        assertThat(sources).allSatisfy(source -> assertThat(source.fetchHoldings()).isNotEmpty());
    }

    @Test
    void dropsManualHoldingsThatAreIncompleteDuplicatedOrNegative() {
        PortfolioProperties properties = new PortfolioProperties();
        properties.getManual().setHoldings(List.of(
                manualHolding(null, "Trezor", "bitcoin-cash", "BCH", 1831, "1"),
                manualHolding("bez-id", "Trezor", "bitcoin-cash", "BCH", null, "1"),
                manualHolding("ujemna", "Trezor", "bitcoin-cash", "BCH", 1831, "-5"),
                manualHolding("dubel", "Trezor", "bitcoin-cash", "BCH", 1831, "2"),
                manualHolding("dubel", "Trezor", "bitcoin-cash", "BCH", 1831, "999")));

        List<HoldingsSource> sources = new HoldingsSourceRegistry(properties, client,
                blockbookClient, hyperliquidClient, new NoCoinbase(), manualHoldings(properties),
                new NoBeanSources()).all();

        assertThat(sources).singleElement().satisfies(source ->
                assertThat(source.fetchHoldings()).singleElement().satisfies(holding ->
                        assertThat(holding.amount()).isEqualByComparingTo("2")));
    }

    private static PortfolioProperties.ManualHolding manualHolding(String key, String source,
                                                                   String network, String asset,
                                                                   Integer cmcId, String amount) {
        PortfolioProperties.ManualHolding holding = new PortfolioProperties.ManualHolding();
        holding.setKey(key);
        holding.setSource(source);
        holding.setNetwork(network);
        holding.setAsset(asset);
        holding.setCmcId(cmcId);
        holding.setAmount(new java.math.BigDecimal(amount));
        return holding;
    }

    /** Bez klucza Coinbase bean jest nullem, a rejestr ma to przejsc bez wyjatku. */
    private static final class NoCoinbase implements ObjectProvider<CoinbaseClient> {
        @Override public CoinbaseClient getIfAvailable() { return null; }
        @Override public CoinbaseClient getIfUnique() { return null; }
        @Override public CoinbaseClient getObject() { throw new UnsupportedOperationException(); }
        @Override public CoinbaseClient getObject(Object... args) { throw new UnsupportedOperationException(); }
        @Override public Stream<CoinbaseClient> stream() { return Stream.empty(); }
        @Override public Stream<CoinbaseClient> orderedStream() { return Stream.empty(); }
    }

    /** No {@code HoldingsSource} beans — the EVM sources are built from configuration only. */
    private static final class NoBeanSources implements ObjectProvider<HoldingsSource> {

        @Override
        public Stream<HoldingsSource> stream() {
            return Stream.empty();
        }

        @Override
        public Stream<HoldingsSource> orderedStream() {
            return Stream.empty();
        }

        @Override
        public HoldingsSource getObject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HoldingsSource getObject(Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HoldingsSource getIfAvailable() {
            return null;
        }

        @Override
        public HoldingsSource getIfUnique() {
            return null;
        }
    }
}

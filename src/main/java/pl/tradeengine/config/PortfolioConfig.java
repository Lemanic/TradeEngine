package pl.tradeengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.tradeengine.adapter.outbound.blockbook.BlockbookClient;
import pl.tradeengine.adapter.outbound.cmc.CoinMarketCapPriceProvider;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseClient;
import pl.tradeengine.adapter.outbound.coinbase.CoinbaseJwt;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidClient;
import pl.tradeengine.adapter.outbound.evm.EvmBalanceClient;
import pl.tradeengine.application.service.portfolio.ManualHoldingService;
import pl.tradeengine.application.service.portfolio.PortfolioQueryService;
import pl.tradeengine.application.service.portfolio.PortfolioSnapshotService;
import pl.tradeengine.domain.port.portfolio.ManualHoldingRepository;
import pl.tradeengine.domain.port.portfolio.PortfolioSnapshotRepository;
import pl.tradeengine.domain.port.portfolio.PriceProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Wiring of the portfolio module. Independent of {@code portfolio.enabled} — that flag only
 *  governs the hourly scheduler, so a manual snapshot works either way. */
@Configuration
public class PortfolioConfig {

    private static final Logger log = LoggerFactory.getLogger(PortfolioConfig.class);

    @Bean
    public Clock portfolioClock() {
        return Clock.systemUTC();
    }

    @Bean
    public EvmBalanceClient evmBalanceClient(PortfolioProperties properties) {
        PortfolioProperties.Evm evm = properties.getEvm();
        return new EvmBalanceClient(RestClient.builder()
                .requestFactory(requestFactory(evm.getConnectTimeout(), evm.getReadTimeout()))
                .build());
    }

    @Bean
    public BlockbookClient blockbookClient(PortfolioProperties properties) {
        PortfolioProperties.Blockbook blockbook = properties.getBlockbook();
        return new BlockbookClient(
                RestClient.builder()
                        .requestFactory(requestFactory(
                                blockbook.getConnectTimeout(), blockbook.getReadTimeout()))
                        .build(),
                blockbook.getUserAgent());
    }

    /** Null when no key is configured, so the registry can skip Coinbase without special cases. */
    @Bean
    public CoinbaseClient coinbaseClient(PortfolioProperties properties, Clock clock) {
        PortfolioProperties.Coinbase coinbase = properties.getCoinbase();
        if (coinbase.getKeyName().isBlank() || coinbase.getPrivateKey().isBlank()) {
            return null;
        }
        RestClient restClient = RestClient.builder()
                .baseUrl(coinbase.getBaseUrl())
                .requestFactory(requestFactory(
                        coinbase.getConnectTimeout(), coinbase.getReadTimeout()))
                .build();
        return new CoinbaseClient(restClient,
                new CoinbaseJwt(coinbase.getKeyName(), coinbase.getPrivateKey(), clock));
    }

    @Bean
    public HyperliquidClient hyperliquidClient(PortfolioProperties properties) {
        PortfolioProperties.Hyperliquid hyperliquid = properties.getHyperliquid();
        return new HyperliquidClient(RestClient.builder()
                .requestFactory(requestFactory(
                        hyperliquid.getConnectTimeout(), hyperliquid.getReadTimeout()))
                .build());
    }

    @Bean
    public PriceProvider coinMarketCapPriceProvider(PortfolioProperties properties) {
        PortfolioProperties.Cmc cmc = properties.getCmc();
        RestClient restClient = RestClient.builder()
                .baseUrl(cmc.getBaseUrl())
                .requestFactory(requestFactory(cmc.getConnectTimeout(), cmc.getReadTimeout()))
                .build();
        return new CoinMarketCapPriceProvider(restClient, cmc.getApiKey());
    }

    @Bean
    public PortfolioSnapshotService portfolioSnapshotService(HoldingsSourceRegistry registry,
                                                             PriceProvider priceProvider,
                                                             PortfolioSnapshotRepository repository,
                                                             Clock clock,
                                                             PortfolioProperties properties) {
        return new PortfolioSnapshotService(registry.all(), priceProvider, repository, clock,
                properties.getCarryForwardMaxAge());
    }

    /**
     * Konfiguracja mowi, JAKIE reczne pozycje istnieja; baza trzyma ICH ILOSC. Dlatego wartosc
     * z YAML-a jest tylko ziarnem — po pierwszym starcie zmienia ja wylacznie endpoint PUT.
     *
     * <p>Bledny wpis jest pomijany z ostrzezeniem, nigdy nie wywraca startu aplikacji.
     *
     * <p>{@code initMethod = "seedMissing"} daje kazdej zadeklarowanej pozycji wiersz w bazie —
     * i robi to w trakcie odswiezania kontekstu, czyli PRZED otwarciem portu. W
     * {@code ApplicationRunner} bylo za pozno: pierwsze zapytanie zdazylo zobaczyc
     * {@code updatedAt: null}, czego kontrakt nie dopuszcza.
     */
    @Bean(initMethod = "seedMissing")
    public ManualHoldingService manualHoldingService(PortfolioProperties properties,
                                                     ManualHoldingRepository repository,
                                                     Clock clock) {
        List<ManualHoldingService.Definition> definitions = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (PortfolioProperties.ManualHolding holding : properties.getManual().getHoldings()) {
            if (isBlank(holding.getKey()) || isBlank(holding.getSource())
                    || isBlank(holding.getNetwork()) || isBlank(holding.getAsset())
                    || holding.getCmcId() == null) {
                log.warn("portfolio.manual.holdings entry needs key, source, network, asset "
                        + "and cmc-id - skipped");
                continue;
            }
            if (!seenKeys.add(holding.getKey())) {
                log.warn("Manual holding key '{}' is used twice - the duplicate is skipped",
                        holding.getKey());
                continue;
            }
            BigDecimal amount = holding.getAmount() == null ? BigDecimal.ZERO : holding.getAmount();
            if (amount.signum() < 0) {
                log.warn("Manual holding '{}' has a negative amount - treated as zero",
                        holding.getKey());
                amount = BigDecimal.ZERO;
            }
            definitions.add(new ManualHoldingService.Definition(holding.getKey(),
                    holding.getSource(), holding.getNetwork(), holding.getAsset(),
                    holding.getCmcId(), amount));
        }

        return new ManualHoldingService(definitions, repository, clock);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Bean
    public PortfolioQueryService portfolioQueryService(PortfolioSnapshotRepository repository) {
        return new PortfolioQueryService(repository);
    }

    private static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}

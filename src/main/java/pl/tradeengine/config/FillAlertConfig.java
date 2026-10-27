package pl.tradeengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.tradeengine.adapter.inbound.FillAlertScheduler;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidClient;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidFillSource;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidHoldingsSource;
import pl.tradeengine.adapter.outbound.hyperliquid.SpotAssetNames;
import pl.tradeengine.application.service.fill.FillAlertService;
import pl.tradeengine.domain.port.NotificationPublisher;
import pl.tradeengine.domain.port.fill.TradeFillRepository;
import pl.tradeengine.domain.port.fill.TradeFillSource;

import java.time.Clock;
import java.util.List;

/**
 * Powiadomienia o zrealizowanych zleceniach na Hyperliquid. Całość istnieje tylko przy
 * {@code portfolio.hyperliquid.fill-alerts.enabled: true} — w testach flaga jest wyłączona.
 *
 * <p>Konfiguracja jest w gałęzi {@code portfolio.hyperliquid} i korzysta z jej adresu, base-url,
 * timeoutów oraz whitelisty spot. To celowe: adres jest ten sam ({@code PORTFOLIO_RABBY_ADDRESS}),
 * a powielenie go pod nowym kluczem tylko dałoby okazję do rozjechania się obu wartości.
 *
 * <p>{@code @EnableScheduling} jest tu drugi raz w aplikacji (obok {@code PortfolioSchedulerConfig})
 * i to jest w porządku — adnotacja jest idempotentna, a alerty o filach mają działać także wtedy,
 * gdy godzinowe snapshoty portfela są wyłączone.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "portfolio.hyperliquid.fill-alerts", name = "enabled",
        havingValue = "true")
public class FillAlertConfig {

    private static final Logger log = LoggerFactory.getLogger(FillAlertConfig.class);

    private final PortfolioProperties properties;

    /**
     * Adres, ktorego fille czytamy — rozwiazany raz, bo jego brak jest ostrzezeniem i ma sie
     * pojawic w logu jeden raz, a nie przy kazdym beanie.
     *
     * <p>Hyperliquid ma w tej konfiguracji jedna etykiete i jeden adres; gdyby doszedl drugi,
     * fille nadal czytamy z pierwszego — rozdzial na kilka adresow bylby decyzja o tresci
     * powiadomien, nie szczegolem technicznym.
     */
    private final String address;

    public FillAlertConfig(PortfolioProperties properties) {
        this.properties = properties;
        this.address = properties.getHyperliquid().getWallets().stream()
                .map(PortfolioProperties.HyperliquidWallet::getAddress)
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .findFirst()
                .orElse("");
        if (address.isBlank()) {
            log.warn("Brak adresu Hyperliquid w portfolio.hyperliquid.wallets —"
                    + " alerty o filach nie beda dzialac");
        }
    }

    @Bean
    public TradeFillSource hyperliquidFillSource(HyperliquidClient hyperliquidClient) {
        PortfolioProperties.Hyperliquid hyperliquid = properties.getHyperliquid();
        return new HyperliquidFillSource(hyperliquid.getBaseUrl(), address, hyperliquidClient,
                new SpotAssetNames(hyperliquidClient, hyperliquid.getBaseUrl()),
                spotTokens(hyperliquid));
    }

    @Bean
    public FillAlertService fillAlertService(TradeFillSource hyperliquidFillSource,
                                             TradeFillRepository tradeFillRepository,
                                             NotificationPublisher notificationPublisher,
                                             Clock portfolioClock) {
        PortfolioProperties.FillAlerts fillAlerts = properties.getHyperliquid().getFillAlerts();
        return new FillAlertService(hyperliquidFillSource, tradeFillRepository,
                notificationPublisher, portfolioClock, fillAlerts.getMaxMessagesPerRun(),
                fillAlerts.getBootstrapLookback(), fillAlerts.getRetention());
    }

    @Bean
    public FillAlertScheduler fillAlertScheduler(FillAlertService fillAlertService) {
        return new FillAlertScheduler(fillAlertService,
                properties.getHyperliquid().getFillAlerts().getPollInterval(),
                !address.isBlank());
    }

    /** Ta sama whitelista, z ktorej korzysta wycena: UETH to ETH, HPENGU to PENGU. */
    private static List<HyperliquidHoldingsSource.SpotToken> spotTokens(
            PortfolioProperties.Hyperliquid hyperliquid) {
        return hyperliquid.getSpotTokens().stream()
                .filter(token -> token.getCoin() != null && token.getAsset() != null)
                .map(token -> new HyperliquidHoldingsSource.SpotToken(
                        token.getCoin(), token.getAsset(),
                        token.getCmcId() == null ? 0 : token.getCmcId()))
                .toList();
    }
}

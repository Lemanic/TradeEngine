package pl.tradeengine.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import pl.tradeengine.adapter.inbound.FillAlertScheduler;
import pl.tradeengine.application.service.fill.FillAlertService;
import pl.tradeengine.domain.port.NotificationPublisher;
import pl.tradeengine.domain.port.fill.TradeFillRepository;
import pl.tradeengine.domain.port.fill.TradeFillSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caly feature wisi na jednej fladze, ktora w profilu test jest wylaczona — bez tego testu zadne
 * uruchomienie nie dotykaloby FillAlertConfig i blad w zlozeniu beanow wyszedlby dopiero na
 * produkcji.
 *
 * <p>Adres celowo nie jest ustawiony: wtedy scheduler nie planuje zadnego zadania, wiec kontekst
 * wstaje bez ani jednego zapytania do gieldy.
 */
@SpringBootTest(properties = "portfolio.hyperliquid.fill-alerts.enabled=true")
@ActiveProfiles("test")
class FillAlertConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void wiresTheWholeChainWhenTheFlagIsOn() {
        assertThat(context.getBean(TradeFillSource.class)).isNotNull();
        assertThat(context.getBean(TradeFillRepository.class)).isNotNull();
        assertThat(context.getBean(FillAlertService.class)).isNotNull();
        assertThat(context.getBean(FillAlertScheduler.class)).isNotNull();
        // Poza prod jedyna implementacja to ta logujaca — Telegram jest @Profile("prod").
        assertThat(context.getBean(NotificationPublisher.class))
                .isInstanceOf(pl.tradeengine.domain.port.LoggingNotificationPublisher.class);
    }
}

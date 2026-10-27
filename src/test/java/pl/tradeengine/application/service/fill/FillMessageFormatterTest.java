package pl.tradeengine.application.service.fill;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.fill.TradeFill;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Tresc wiadomosci: kazde pole filla ma swoje miejsce albo nie pojawia sie wcale. */
class FillMessageFormatterTest {

    private static final Instant TIME = Instant.parse("2026-10-27T14:32:11Z");

    @Test
    void describesOpeningAPerpPosition() {
        TradeFill fill = new TradeFill(1L, TIME, "BTC", "BTC", TradeFill.Side.BUY, "Open Long",
                new BigDecimal("0.0123"), new BigDecimal("108450.5"), BigDecimal.ZERO,
                new BigDecimal("0.47"), "USDC", false);

        assertThat(FillMessageFormatter.format(fill)).isEqualTo("""
                🟢 HYPERLIQUID · Open Long
                BTC  0.0123 @ 108450.5
                Wartość: 1333.94 USD
                Prowizja: 0.47 USDC
                2026-10-27 14:32 UTC""");
    }

    @Test
    void showsRealisedPnlWhenAPositionIsReduced() {
        TradeFill fill = new TradeFill(2L, TIME, "BTC", "BTC", TradeFill.Side.SELL, "Reduce Long",
                new BigDecimal("0.0123"), new BigDecimal("109000"), new BigDecimal("6.73"),
                BigDecimal.ZERO, "USDC", false);

        assertThat(FillMessageFormatter.format(fill)).isEqualTo("""
                🔴 HYPERLIQUID · Reduce Long
                BTC  0.0123 @ 109000
                Wartość: 1340.70 USD
                PnL: +6.73 USDC
                2026-10-27 14:32 UTC""");
    }

    @Test
    void marksSpotAndKeepsSmallPricesIntact() {
        TradeFill fill = new TradeFill(3L, TIME, "@107", "PENGU", TradeFill.Side.BUY, "Buy",
                new BigDecimal("1000"), new BigDecimal("0.0121"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, true);

        assertThat(FillMessageFormatter.format(fill)).isEqualTo("""
                🟢 HYPERLIQUID · Buy (spot)
                PENGU  1000 @ 0.0121
                Wartość: 12.10 USD
                2026-10-27 14:32 UTC""");
    }

    @Test
    void keepsTheMinusOnALoss() {
        TradeFill fill = new TradeFill(4L, TIME, "ETH", "ETH", TradeFill.Side.SELL, "Reduce Short",
                new BigDecimal("1"), new BigDecimal("3000"), new BigDecimal("-12.5"),
                new BigDecimal("1.2"), "USDC", false);

        assertThat(FillMessageFormatter.format(fill))
                .contains("PnL: -12.50 USDC")
                .contains("Prowizja: 1.20 USDC");
    }

    @Test
    void fallsBackToTheSideWhenTheApiSendsNoDirection() {
        TradeFill fill = new TradeFill(5L, TIME, "kPEPE", "kPEPE", TradeFill.Side.SELL, null,
                new BigDecimal("10"), new BigDecimal("0.5"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, false);

        assertThat(FillMessageFormatter.format(fill)).startsWith("🔴 HYPERLIQUID · Sell\nkPEPE");
    }

    @Test
    void describesAnUnforeseenEventWithTheApisOwnWords() {
        // Likwidacja nie ma w kodzie zadnej galezi — opisuje sie sama przez dir z API.
        TradeFill fill = new TradeFill(6L, TIME, "BTC", "BTC", TradeFill.Side.SELL,
                "Liquidated Isolated Long", new BigDecimal("0.5"), new BigDecimal("95000"),
                new BigDecimal("-480.25"), BigDecimal.ZERO, "USDC", false);

        assertThat(FillMessageFormatter.format(fill))
                .startsWith("🔴 HYPERLIQUID · Liquidated Isolated Long")
                .contains("PnL: -480.25 USDC");
    }

    @Test
    void theBootstrapMessageSaysNothingWillComeForThePast() {
        assertThat(FillMessageFormatter.bootstrap(7, Duration.ofHours(24)))
                .contains("Zapamiętano 7")
                .contains("24 h")
                .contains("bez powiadamiania");
    }
}

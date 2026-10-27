package pl.tradeengine.application.service.fill;

import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.util.PriceFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Treść wiadomości o zrealizowanym zleceniu.
 *
 * <p>Nagłówek bierze {@code dir} z API dosłownie ({@code Open Long}, {@code Reduce Short},
 * {@code Buy}) — dzięki temu zdarzenie, którego nie przewidzieliśmy, opisze się samo. Gdy
 * {@code dir} nie przyszedł, zostaje kierunek wyliczony ze strony zlecenia.
 *
 * <p>Ilość i cena przez {@link PriceFormatter}, czyli bez zaokrąglania — przy cenie w rodzaju
 * 0,0121 dwa miejsca po przecinku skłamałyby. Wartość i PnL są zaokrąglone do dwóch miejsc, bo
 * tam chodzi o dolary.
 */
public final class FillMessageFormatter {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private static final String DEFAULT_FEE_TOKEN = "USDC";

    private FillMessageFormatter() {}

    public static String format(TradeFill fill) {
        StringBuilder message = new StringBuilder();
        message.append(fill.side() == TradeFill.Side.BUY ? "🟢" : "🔴")
                .append(" HYPERLIQUID · ")
                .append(header(fill))
                .append('\n')
                .append(fill.displayName())
                .append("  ")
                .append(PriceFormatter.format(fill.size()))
                .append(" @ ")
                .append(PriceFormatter.format(fill.price()))
                .append('\n')
                .append("Wartość: ")
                .append(money(fill.notional()))
                .append(" USD");

        if (fill.closedPnl() != null && fill.closedPnl().signum() != 0) {
            message.append("\nPnL: ")
                    .append(signed(fill.closedPnl()))
                    .append(' ')
                    .append(feeToken(fill));
        }
        if (fill.fee() != null && fill.fee().signum() != 0) {
            message.append("\nProwizja: ")
                    .append(money(fill.fee()))
                    .append(' ')
                    .append(feeToken(fill));
        }

        return message.append('\n').append(STAMP.format(fill.time())).append(" UTC").toString();
    }

    /**
     * Wiadomość z pierwszego przebiegu. Potwierdza, że cała droga do Telegrama działa, i mówi
     * wprost, że o filach z przeszłości alertu nie będzie — inaczej cisza byłaby dwuznaczna.
     */
    public static String bootstrap(int remembered, Duration lookback) {
        return "🔔 HYPERLIQUID\n"
                + "Alerty o realizacji zleceń włączone.\n"
                + "Zapamiętano " + remembered + " fill(e) z ostatnich " + lookback.toHours()
                + " h bez powiadamiania — alert przyjdzie przy następnej realizacji.";
    }

    /** Dopisek, gdy jeden przebieg zobaczył więcej filli, niż wolno mu wysłać wiadomości. */
    public static String skipped(int skipped, int limit) {
        return "… +" + skipped + " kolejnych filli pominięto (limit " + limit
                + " wiadomości na przebieg). Szczegóły w historii na Hyperliquid.";
    }

    private static String header(TradeFill fill) {
        String direction = fill.dir() == null || fill.dir().isBlank()
                ? (fill.side() == TradeFill.Side.BUY ? "Buy" : "Sell")
                : fill.dir();
        return fill.spot() ? direction + " (spot)" : direction;
    }

    private static String feeToken(TradeFill fill) {
        return fill.feeToken() == null || fill.feeToken().isBlank()
                ? DEFAULT_FEE_TOKEN
                : fill.feeToken();
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String signed(BigDecimal amount) {
        String value = money(amount);
        return amount.signum() > 0 ? "+" + value : value;
    }
}

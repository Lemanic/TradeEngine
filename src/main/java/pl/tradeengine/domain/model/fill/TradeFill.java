package pl.tradeengine.domain.model.fill;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Jedno zrealizowane zlecenie na giełdzie — spotowe kupno/sprzedaż albo otwarcie/redukcja pozycji
 * na perpach.
 *
 * <p>{@code tid} to identyfikator transakcji nadany przez giełdę i on decyduje o tym, czy fill
 * jest nowy: jeden fill to jeden {@code tid}, także gdy jedno zlecenie zostało zrealizowane w
 * kilku kawałkach. Dlatego trafia do bazy jako klucz główny.
 *
 * <p>{@code coin} jest tym, co podało API ({@code BTC}, ale dla spotu bywa {@code @107}),
 * a {@code displayName} tym, co idzie do wiadomości. Rozdzielone, bo kursor i dedup muszą
 * trzymać się surowej nazwy, a człowiek czyta tickera.
 *
 * <p>{@code dir} przepisujemy dosłownie z API ({@code Open Long}, {@code Reduce Short},
 * {@code Buy}, {@code Sell}) — dzięki temu zdarzenie, którego nie przewidzieliśmy (likwidacja)
 * opisze się samo, bez zmian w kodzie.
 */
public record TradeFill(long tid,
                        Instant time,
                        String coin,
                        String displayName,
                        Side side,
                        String dir,
                        BigDecimal size,
                        BigDecimal price,
                        BigDecimal closedPnl,
                        BigDecimal fee,
                        String feeToken,
                        boolean spot) {

    public enum Side { BUY, SELL }

    /** Wartość transakcji w walucie kwotowanej, czyli ilość razy cena. */
    public BigDecimal notional() {
        return size.multiply(price);
    }
}

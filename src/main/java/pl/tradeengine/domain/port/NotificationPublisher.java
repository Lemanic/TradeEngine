package pl.tradeengine.domain.port;

/**
 * Jedna wiadomość tekstowa do właściciela aplikacji.
 *
 * <p>Osobny port od {@link AlertPublisher}, bo tamten przenosi {@code AlertToSend} — model
 * skrojony pod strategie ({@code Symbol}, {@code Direction} tylko LONG/SHORT, {@code Timeframe},
 * nazwa scenariusza, SL i TP). Spotowe kupno nie ma ani kierunku w tym sensie, ani interwału,
 * więc przeszłoby tam wyłącznie z nullami — a {@code BacktestRunner} już dziś czyta te gettery.
 */
public interface NotificationPublisher {

    /**
     * Wysyła wiadomość. Implementacje nie wyrzucają wyjątku w górę: nieudana wysyłka nie może
     * wywrócić przebiegu, który ją zlecił, ani zablokować zapisu „już obsłużone”.
     */
    void send(String text);
}

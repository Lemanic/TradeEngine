package pl.tradeengine.adapter.outbound.hyperliquid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotMeta;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotTokenMeta;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.SpotUniverseEntry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Zamienia spotową nazwę rynku z filla ({@code "@107"}) na tickera tokena ({@code "HPENGU"}).
 *
 * <p>Słownik {@code spotMeta} ma ponad tysiąc wpisów i prawie się nie zmienia, więc jest
 * czytany leniwie i trzymany w pamięci. Nieznana nazwa oznacza nowy rynek i wywołuje jedno
 * odświeżenie, ale nie częściej niż raz na {@link #REFRESH_COOLDOWN} — inaczej każdy fill na
 * rynku, którego API nie zna, dokładałby zapytanie w każdym przebiegu.
 *
 * <p>Awaria tego zapytania nigdy nie przewraca powiadomienia: wiadomość pójdzie z surowym
 * {@code "@107"}, co jest brzydkie, ale uczciwe i lepsze niż brak alertu o zrealizowanym zleceniu.
 */
public class SpotAssetNames {

    private static final Logger log = LoggerFactory.getLogger(SpotAssetNames.class);

    private static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(10);

    private final HyperliquidClient client;
    private final String baseUrl;

    private volatile Map<String, String> tickerByMarket = Map.of();
    private Long lastAttemptNanos;

    public SpotAssetNames(HyperliquidClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    /** Ticker tokena bazowego rynku, albo puste gdy słownik go nie zna. */
    public Optional<String> tickerOf(String market) {
        String known = tickerByMarket.get(market);
        if (known != null) {
            return Optional.of(known);
        }
        if (refreshAllowed()) {
            refresh();
        }
        return Optional.ofNullable(tickerByMarket.get(market));
    }

    private synchronized boolean refreshAllowed() {
        long now = System.nanoTime();
        if (lastAttemptNanos != null && now - lastAttemptNanos < REFRESH_COOLDOWN.toNanos()) {
            return false;
        }
        lastAttemptNanos = now;
        return true;
    }

    private void refresh() {
        SpotMeta meta;
        try {
            meta = client.spotMeta(baseUrl);
        } catch (RuntimeException e) {
            log.warn("Nie udalo sie odczytac slownika rynkow spot: {}", e.toString());
            return;
        }

        List<SpotUniverseEntry> universe = meta.universe();
        List<SpotTokenMeta> tokens = meta.tokens();
        if (universe == null || tokens == null) {
            log.warn("Slownik rynkow spot przyszedl bez universe albo tokens");
            return;
        }

        Map<Integer, String> nameByTokenIndex = new HashMap<>();
        for (SpotTokenMeta token : tokens) {
            if (token.index() != null && token.name() != null) {
                nameByTokenIndex.put(token.index(), token.name());
            }
        }

        Map<String, String> resolved = new HashMap<>();
        for (SpotUniverseEntry market : universe) {
            if (market.name() == null || market.tokens() == null || market.tokens().isEmpty()) {
                continue;
            }
            // tokens[0] to token bazowy, tokens[1] to waluta kwotowana (zwykle USDC).
            String base = nameByTokenIndex.get(market.tokens().getFirst());
            if (base != null) {
                resolved.put(market.name(), base);
            }
        }

        tickerByMarket = Map.copyOf(resolved);
        log.info("Slownik rynkow spot: {} rynkow", resolved.size());
    }
}

package pl.tradeengine.adapter.outbound.hyperliquid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.Fill;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.OrderDetails;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidResponses.OrderStatus;
import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.port.fill.TradeFillSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Zrealizowane zlecenia jednego adresu na Hyperliquid — spot i perpy z jednego zapytania
 * {@code userFillsByTime}.
 *
 * <p>Cztery rzeczy, których nie widać w API:
 * <ul>
 *   <li><b>Spot ma inny {@code coin} niż perp</b> — zamiast tickera przychodzi indeks rynku
 *       ({@code "@107"}) albo nazwa pary ({@code "PURR/USDC"}). Do wiadomości idzie ticker
 *       rozwiązany przez {@link SpotAssetNames}; do bazy surowa nazwa, bo dedup nie może zależeć
 *       od tego, czy słownik akurat odpowiedział.</li>
 *   <li><b>Owinięte tokeny nazywają się inaczej, niż są warte</b> — {@code UETH} to ETH,
 *       {@code HPENGU} to PENGU. Aliasy bierzemy z tej samej whitelisty
 *       {@code portfolio.hyperliquid.spot-tokens}, z której korzysta wycena portfela, więc jedna
 *       konfiguracja obsługuje oba zastosowania.</li>
 *   <li><b>Fill bez {@code tid} albo {@code time} jest bezużyteczny</b> — bez nich nie da się go
 *       zapamiętać jako obsłużonego, więc alert o nim wracałby w każdym przebiegu. Takie wiersze
 *       są pomijane z ostrzeżeniem.</li>
 *   <li><b>Fill nie mówi, jakim zleceniem powstał.</b> Pole {@code crossed} (taker) nie wystarcza:
 *       TP i SL to też markety po stronie takera, a limit złożony po drugiej stronie księgi
 *       realizuje się od razu jako taker. Dlatego o każdy fill pytamy {@code orderStatus} i za
 *       ręczne uznajemy tylko {@code orderType = Market} z {@code tif = FrontendMarket} — to jest
 *       kliknięcie w interfejsie, perp albo spot, otwarcie albo zamknięcie. TP i SL mają
 *       {@code Stop Market}/{@code Take Profit Market}, a market złożony przez API (bot) inne
 *       {@code tif}, więc alert dostają. Likwidacji nie sprawdzamy wcale — jej alert nie może
 *       zależeć od tego, co odpowie giełda. Gdy {@code orderStatus} nie odpowie, alert idzie
 *       normalnie: zbędna wiadomość jest lepsza niż przegapiony limit.</li>
 * </ul>
 */
public class HyperliquidFillSource implements TradeFillSource {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidFillSource.class);

    private static final String BUY_SIDE = "B";
    private static final String LIQUIDATION_DIR_MARKER = "Liquidat";
    private static final String MARKET_ORDER_TYPE = "Market";
    private static final String FRONTEND_TIF = "FrontendMarket";

    private final String baseUrl;
    private final String address;
    private final HyperliquidClient client;
    private final SpotAssetNames spotAssetNames;
    private final Map<String, String> assetAliases;

    public HyperliquidFillSource(String baseUrl, String address, HyperliquidClient client,
                                 SpotAssetNames spotAssetNames,
                                 List<HyperliquidHoldingsSource.SpotToken> spotTokens) {
        this.baseUrl = baseUrl;
        this.address = address;
        this.client = client;
        this.spotAssetNames = spotAssetNames;
        this.assetAliases = spotTokens.stream()
                .collect(Collectors.toMap(HyperliquidHoldingsSource.SpotToken::coin,
                        HyperliquidHoldingsSource.SpotToken::asset,
                        (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public List<TradeFill> fillsSince(Instant since) {
        Fill[] fills = client.userFillsByTime(baseUrl, address, since.toEpochMilli());

        // Jedno zlecenie market potrafi zrealizowac sie w kilku fillach — pytamy raz na oid.
        Map<Long, Boolean> manualByOid = new HashMap<>();
        List<TradeFill> mapped = new ArrayList<>();
        for (Fill fill : fills) {
            if (!usable(fill)) {
                log.warn("Pomijam fill bez wymaganych pol: tid={}, time={}, coin={}",
                        fill.tid(), fill.time(), fill.coin());
                continue;
            }
            mapped.add(toDomain(fill, manualByOid));
        }
        mapped.sort(Comparator.comparing(TradeFill::time).thenComparingLong(TradeFill::tid));

        log.debug("Hyperliquid: {} fill(ow) od {}", mapped.size(), since);
        return mapped;
    }

    private static boolean usable(Fill fill) {
        return fill.tid() != null && fill.time() != null && fill.coin() != null
                && fill.px() != null && fill.sz() != null;
    }

    private TradeFill toDomain(Fill fill, Map<Long, Boolean> manualByOid) {
        boolean spot = isSpot(fill.coin());
        return new TradeFill(
                fill.tid(),
                Instant.ofEpochMilli(fill.time()),
                fill.coin(),
                displayName(fill.coin(), spot),
                BUY_SIDE.equalsIgnoreCase(fill.side()) ? TradeFill.Side.BUY : TradeFill.Side.SELL,
                fill.dir(),
                fill.sz(),
                fill.px(),
                fill.closedPnl() == null ? BigDecimal.ZERO : fill.closedPnl(),
                fill.fee() == null ? BigDecimal.ZERO : fill.fee(),
                fill.feeToken(),
                spot,
                manualMarketOrder(fill, manualByOid));
    }

    private boolean manualMarketOrder(Fill fill, Map<Long, Boolean> manualByOid) {
        if (fill.oid() == null
                || (fill.dir() != null && fill.dir().contains(LIQUIDATION_DIR_MARKER))) {
            return false;
        }
        return manualByOid.computeIfAbsent(fill.oid(), this::isManualMarketOrder);
    }

    private boolean isManualMarketOrder(long oid) {
        OrderStatus status;
        try {
            status = client.orderStatus(baseUrl, address, oid);
        } catch (HyperliquidException e) {
            log.warn("Nie znam typu zlecenia oid={}, alert pojdzie: {}", oid, e.getMessage());
            return false;
        }
        OrderDetails order = status.order() == null ? null : status.order().order();
        if (order == null) {
            log.warn("Hyperliquid nie zna zlecenia oid={} ({}), alert pojdzie", oid, status.status());
            return false;
        }
        return MARKET_ORDER_TYPE.equals(order.orderType()) && FRONTEND_TIF.equals(order.tif());
    }

    /** Rynek spot zapisany jest indeksem ({@code "@107"}) albo parą ({@code "PURR/USDC"}). */
    private static boolean isSpot(String coin) {
        return coin.startsWith("@") || coin.contains("/");
    }

    private String displayName(String coin, boolean spot) {
        String ticker = coin;
        if (spot) {
            ticker = coin.startsWith("@")
                    ? spotAssetNames.tickerOf(coin).orElse(coin)
                    : coin.substring(0, coin.indexOf('/'));
        }
        return assetAliases.getOrDefault(ticker, ticker);
    }
}

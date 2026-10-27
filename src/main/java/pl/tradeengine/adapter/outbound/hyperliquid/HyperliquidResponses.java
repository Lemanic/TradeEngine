package pl.tradeengine.adapter.outbound.hyperliquid;

import java.math.BigDecimal;
import java.util.List;

/**
 * The slices of Hyperliquid's {@code /info} responses this module needs. Numbers arrive as
 * decimal strings and Jackson converts them to {@link BigDecimal} without going through a
 * {@code double}.
 */
public final class HyperliquidResponses {

    private HyperliquidResponses() {}

    /**
     * Perp account. {@code accountValue} is the account's equity in USD <em>after</em> unrealised
     * PnL — not a token balance, which is why it needs its own treatment when mapped to a holding.
     */
    public record ClearinghouseState(MarginSummary marginSummary) {}

    public record MarginSummary(BigDecimal accountValue) {}

    /** Spot balances. {@code total} includes amounts held against open orders. */
    public record SpotClearinghouseState(List<SpotBalance> balances) {}

    public record SpotBalance(String coin, Integer token, BigDecimal total) {}

    /** Staking. {@code delegated} is the amount currently delegated to validators. */
    public record DelegatorSummary(BigDecimal delegated) {}

    /**
     * Jeden zrealizowany fill z {@code userFills}/{@code userFillsByTime}.
     *
     * <p>Wszystkie pola są obiektami, nie primitywami, bo API pomija te, które są puste
     * ({@code builderFee} przy zerowej prowizji buildera) — a brakujący {@code tid} albo
     * {@code time} oznacza wiersz, którego nie wolno uznać za obsłużony.
     *
     * <p>{@code side} to {@code "B"} (kupno) albo {@code "A"} (sprzedaż). {@code dir} opisuje to
     * samo słowami i bogaciej: {@code Open Long}, {@code Reduce Short}, {@code Buy}, {@code Sell}.
     * {@code coin} dla perpa jest tickerem, dla spotu indeksem w postaci {@code "@107"}.
     */
    public record Fill(Long tid,
                       Long time,
                       String coin,
                       BigDecimal px,
                       BigDecimal sz,
                       String side,
                       String dir,
                       BigDecimal closedPnl,
                       BigDecimal fee,
                       String feeToken,
                       Long oid) {}

    /**
     * Słownik rynków spot. Potrzebny tylko po to, żeby {@code "@107"} z filla zamienić na
     * czytelnego tickera: {@code universe[].name} równe {@code "@107"} wskazuje przez
     * {@code tokens[0]} na indeks tokena w {@code tokens[]}.
     */
    public record SpotMeta(List<SpotUniverseEntry> universe, List<SpotTokenMeta> tokens) {}

    public record SpotUniverseEntry(String name, List<Integer> tokens, Integer index) {}

    public record SpotTokenMeta(String name, Integer index) {}
}

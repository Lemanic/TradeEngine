package pl.tradeengine.adapter.outbound.manual;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.portfolio.Holding;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ManualHoldingsSourceTest {

    private final Map<String, BigDecimal> amounts = new HashMap<>();

    private ManualHoldingsSource source(ManualHoldingsSource.Entry... entries) {
        return new ManualHoldingsSource("Trezor", List.of(entries), amounts::get);
    }

    private static ManualHoldingsSource.Entry entry(String key, String network, String asset,
                                                    int cmcId) {
        return new ManualHoldingsSource.Entry(key, network, asset, cmcId);
    }

    @Test
    void reportsStoredAmountsUnderTheGivenLabel() {
        amounts.put("trezor-bch", new BigDecimal("21.5"));
        ManualHoldingsSource source = source(entry("trezor-bch", "bitcoin-cash", "BCH", 1831));

        assertThat(source.name()).isEqualTo("Trezor");
        assertThat(source.networks()).containsExactly("bitcoin-cash");
        assertThat(source.fetchHoldings())
                .extracting(Holding::source, Holding::network, Holding::asset, Holding::cmcId)
                .containsExactly(tuple("Trezor", "bitcoin-cash", "BCH", 1831));
        assertThat(source.fetchHoldings().getFirst().amount()).isEqualByComparingTo("21.5");
    }

    @Test
    void keepsFullPrecisionOfTheTypedAmount() {
        amounts.put("trezor-bch", new BigDecimal("21.50391827"));

        assertThat(source(entry("trezor-bch", "bitcoin-cash", "BCH", 1831))
                .fetchHoldings().getFirst().amount().toPlainString())
                .isEqualTo("21.50391827");
    }

    /**
     * Istota rozdzialu konfiguracji od ilosci: PUT zmienia ilosc, a zrodlo widzi nowa wartosc przy
     * nastepnym snapshocie, bez restartu i bez przebudowy rejestru.
     */
    @Test
    void seesAnAmountChangedAfterTheSourceWasBuilt() {
        amounts.put("tao-staking", new BigDecimal("11.37"));
        ManualHoldingsSource source = source(entry("tao-staking", "bittensor", "TAO", 22974));
        assertThat(source.fetchHoldings().getFirst().amount()).isEqualByComparingTo("11.37");

        amounts.put("tao-staking", new BigDecimal("12.50"));

        assertThat(source.fetchHoldings().getFirst().amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void skipsAZeroAmountButKeepsTheEntryDeclared() {
        // zero znaczy "nic tu teraz nie trzymam"; wpis zostaje i wroci, gdy ilosc urosnie
        amounts.put("trezor-bch", BigDecimal.ZERO);
        ManualHoldingsSource source = source(entry("trezor-bch", "bitcoin-cash", "BCH", 1831));

        assertThat(source.fetchHoldings()).isEmpty();
        assertThat(source.networks()).containsExactly("bitcoin-cash");
    }

    /** Brak wiersza w bazie nie moze wywrocic snapshotu — pozycja po prostu jeszcze nie waży. */
    @Test
    void treatsAnUnknownAmountAsZeroRatherThanFailing() {
        ManualHoldingsSource source = source(entry("trezor-bch", "bitcoin-cash", "BCH", 1831));

        assertThat(source.fetchHoldings()).isEmpty();
    }

    @Test
    void carriesSeveralEntriesOfOneLabelAcrossNetworks() {
        amounts.put("trezor-bch", new BigDecimal("21.5"));
        amounts.put("trezor-ltc", new BigDecimal("3.25"));
        ManualHoldingsSource source = source(
                entry("trezor-bch", "bitcoin-cash", "BCH", 1831),
                entry("trezor-ltc", "litecoin", "LTC", 2));

        assertThat(source.networks()).containsExactly("bitcoin-cash", "litecoin");
        assertThat(source.fetchHoldings()).extracting(Holding::asset).containsExactly("BCH", "LTC");
    }

    @Test
    void neverThrowsSoItNeverTriggersCarryForward() {
        ManualHoldingsSource empty = source();

        assertThat(empty.fetchHoldings()).isEmpty();
        assertThat(empty.networks()).isEmpty();
    }
}

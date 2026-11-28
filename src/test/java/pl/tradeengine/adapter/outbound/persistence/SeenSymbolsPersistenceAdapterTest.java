package pl.tradeengine.adapter.outbound.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Cztery zapytania JPQL po czterech tabelach — fake by ich nie sprawdzil. */
@DataJpaTest
@Import(SeenSymbolsPersistenceAdapter.class)
@ActiveProfiles("test")
class SeenSymbolsPersistenceAdapterTest {

    private static final ZonedDateTime SINCE = ZonedDateTime.parse("2026-10-29T12:00:00Z");
    private static final ZonedDateTime RECENT = SINCE.plusDays(10);
    private static final ZonedDateTime OLD = SINCE.minusDays(1);

    @Autowired
    private SeenSymbolsPersistenceAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void collectsSymbolsFromEveryWebhookTable_withoutDuplicates() {
        entityManager.persist(new DivergenceEntity(null, "BTCUSDT", Timeframe.H1, Direction.LONG, 1.0, RECENT));
        entityManager.persist(new DivergenceEntity(null, "BTCUSDT", Timeframe.H4, Direction.SHORT, 1.0, RECENT));
        entityManager.persist(new SwingPointEntity("NQ1!", Timeframe.H1, "HIGH", BigDecimal.ONE, RECENT));
        entityManager.persist(new BiasEntity("GOLD", Timeframe.D1, BiasStatus.values()[0], "test", RECENT));
        entityManager.persist(touchedFvg("TAOUSDT", RECENT));

        assertThat(adapter.since(SINCE)).extracting(Symbol::code)
                .containsExactlyInAnyOrder("BTCUSDT", "NQ1!", "GOLD", "TAOUSDT");
    }

    @Test
    void leavesOutWhatWasLastSeenBeforeTheWindow() {
        entityManager.persist(new DivergenceEntity(null, "OLDUSDT", Timeframe.H1, Direction.LONG, 1.0, OLD));
        entityManager.persist(new SwingPointEntity("OLDUSDT", Timeframe.H1, "HIGH", BigDecimal.ONE, OLD));
        entityManager.persist(touchedFvg("OLDUSDT", OLD));

        assertThat(adapter.since(SINCE)).isEmpty();
    }

    @Test
    void anUntouchedFvgHasNoTimeAndDoesNotCount() {
        // fvg_zone nie ma czasu utworzenia — udokumentowana luka, para jest widoczna dopiero po dotknieciu.
        entityManager.persist(new FvgEntity(null, "FVGONLY", Timeframe.H1, Direction.LONG,
                BigDecimal.ONE, BigDecimal.TEN, 1.0, FvgKind.values()[0], FvgStatus.CREATED));

        assertThat(adapter.since(SINCE)).isEmpty();
    }

    private static FvgEntity touchedFvg(String symbol, ZonedDateTime touchedAt) {
        FvgEntity fvg = new FvgEntity(null, symbol, Timeframe.H4, Direction.LONG,
                BigDecimal.ONE, BigDecimal.TEN, 1.0, FvgKind.values()[0], FvgStatus.TOUCHED);
        fvg.setTouchedAt(touchedAt);
        return fvg;
    }
}

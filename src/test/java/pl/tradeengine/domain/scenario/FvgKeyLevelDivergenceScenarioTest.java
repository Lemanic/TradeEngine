package pl.tradeengine.domain.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.BTC;
import static pl.tradeengine.TestFixtures.FIXED_NOW;

/**
 * Strategia o dwoch wejsciach, ktora nie miala zadnego testu.
 *
 * <p>Nazwy w produkcji sa mylace: "post/inside" to dywergencja przychodzaca do istniejacej strefy
 * HTF, a "pre-touch" to dotkniecie strefy, przed ktorym byla dywergencja. Pierwsza sciezka ma
 * wymog podwojnosci <b>tylko na M5</b>, druga patrzy na staly zestaw [M5, M15].
 */
class FvgKeyLevelDivergenceScenarioTest {

    private InMemoryFvgRepository fvgRepo;
    private InMemoryDivergenceRepository divergenceRepo;
    private FvgKeyLevelDivergenceScenario scenario;

    @BeforeEach
    void setUp() {
        fvgRepo = new InMemoryFvgRepository();
        divergenceRepo = new InMemoryDivergenceRepository();
        scenario = new FvgKeyLevelDivergenceScenario(fvgRepo, divergenceRepo);
    }

    // ─────────────────────────────────────────────────────────────
    // Rozdzielnia zdarzen
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnNoAlerts_whenEventTypeIsNotHandled() {
        DomainEvent unknown = new DomainEvent() {};

        assertThat(scenario.onEvent(unknown)).isEmpty();
    }

    @Test
    void shouldExposeStableNameForYamlWiring() {
        // Zmiana tej nazwy bez aktualizacji app.strategies cicho wylacza strategie — patrz
        // ScenarioRegistryTest.shouldSilentlySkipYamlEntry_whenNoBeanHasThatName.
        assertThat(scenario.name()).isEqualTo("FVG_KEYLEVEL_DIVERGENCE_STRATEGY");
    }

    // ─────────────────────────────────────────────────────────────
    // Dywergencja do istniejacej strefy HTF
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnNoAlerts_whenM5DivergenceIsSingle() {
        // Jedyny timeframe z wymogiem podwojnosci. Pojedynczy sygnal na M5 to szum.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        DivergenceSignal single = save(Timeframe.M5, Direction.LONG, FIXED_NOW);

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(single))).isEmpty();
    }

    @Test
    void shouldEmitAlert_whenM5DivergenceIsDouble() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        save(Timeframe.M5, Direction.LONG, FIXED_NOW.minusMinutes(10));
        DivergenceSignal second = save(Timeframe.M5, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(second));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).startsWith("DIV+FVG |");
    }

    @Test
    void shouldSkipTheDoubleRequirement_onEveryTimeframeOtherThanM5() {
        // M15, H1 i H4 przechodza z jednym sygnalem — kontekst HTF jest uznany za wystarczajacy.
        for (Timeframe tf : new Timeframe[]{Timeframe.M15, Timeframe.H1, Timeframe.H4}) {
            setUp();
            TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.D1, Direction.SHORT, FIXED_NOW.minusHours(2));
            DivergenceSignal single = save(tf, Direction.SHORT, FIXED_NOW);

            assertThat(scenario.onEvent(new DivergenceDetectedEvent(single)))
                    .as("pojedyncza dywergencja na %s", tf)
                    .hasSize(1);
        }
    }

    @Test
    void shouldReturnNoAlerts_whenNoHtfFvgIsActiveForThatDirection() {
        // Strefa jest, ale w przeciwnym kierunku — zapytanie filtruje po kierunku.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.SHORT, FIXED_NOW.minusHours(1));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, FIXED_NOW);

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).isEmpty();
    }

    @Test
    void shouldIgnoreFvgsBelowTheHtfSet_whenLookingForContext() {
        // Zapytanie idzie z [H1, H4, D1]. Strefa na M15 nie jest kontekstem HTF, nawet dotknieta.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.M15, Direction.LONG, FIXED_NOW.minusMinutes(30));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, FIXED_NOW);

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).isEmpty();
    }

    @Test
    void shouldAcceptFilledFvgAsContext_notOnlyTouched() {
        // Statusy w zapytaniu to [TOUCHED, FILLED] — wypelniona strefa nadal jest poziomem.
        TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H1, Direction.LONG, FIXED_NOW.minusHours(3));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).contains("FILLED");
    }

    @Test
    void shouldEmitExactlyOneAlert_whenSeveralHtfFvgsQualify() {
        // Produkcja bierze candidateFvgs.get(0), a port nie obiecuje zadnej kolejnosci — wiec
        // utrwalona jest liczba alertow, nie wybor konkretnej strefy.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H1, Direction.LONG, FIXED_NOW.minusHours(1));
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(2));
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.D1, Direction.LONG, FIXED_NOW.minusHours(5));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, FIXED_NOW);

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).hasSize(1);
    }

    @Test
    void shouldFillAlertFieldsFromSignalAndZone() {
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, FIXED_NOW);

        AlertToSend alert = scenario.onEvent(new DivergenceDetectedEvent(signal)).get(0);

        assertThat(alert.getSymbol()).isEqualTo(BTC);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getScenarioName()).isEqualTo("FVG_KEYLEVEL_DIVERGENCE_STRATEGY");
        // Timeframe alertu to timeframe DYWERGENCJI, nie strefy HTF.
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.H1);
        assertThat(alert.getEntryPrice()).isEqualByComparingTo("21.37");
        assertThat(alert.getStopLoss()).isEmpty();
        assertThat(alert.getTakeProfit()).isEmpty();
        // Ceny strefy przechodza przez PriceFormatter, wiec bez koncowych zer.
        assertThat(alert.getDescription())
                .contains("DIV H1")
                .contains("FVG H4 TOUCHED")
                .contains("SCORE 6.9/10")
                .contains("60000-61000");
    }

    // ─────────────────────────────────────────────────────────────
    // Dotkniecie strefy po wczesniejszej dywergencji (pre-touch)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnNoAlerts_whenNoDivergencePrecededTheTouch() {
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW);

        assertThat(scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldEmitPreTouchAlert_whenAnM5DivergencePrecededTheTouch() {
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW);
        // Okno to touchedAt - 10 * M5 = 50 minut.
        save(Timeframe.M5, Direction.LONG, FIXED_NOW.minusMinutes(20));

        List<AlertToSend> alerts = scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription())
                .startsWith("PRE-TOUCH |")
                .contains("DIV M5")
                .contains("FVG H4");
        assertThat(alerts.get(0).getTimeframe()).isEqualTo(Timeframe.M5);
    }

    @Test
    void shouldIgnoreDivergenceOlderThanTheTenCandleWindow() {
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW);
        // M5: okno 50 minut; M15: okno 150 minut. 4 godziny wstecz wypada poza oba.
        save(Timeframe.M5, Direction.LONG, FIXED_NOW.minusHours(4));
        save(Timeframe.M15, Direction.LONG, FIXED_NOW.minusHours(4));

        assertThat(scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldIgnoreDivergenceInTheOppositeDirection() {
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW);
        save(Timeframe.M5, Direction.SHORT, FIXED_NOW.minusMinutes(10));

        assertThat(scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldPickTheLastOfTheConcatenatedList_whichIsM15RatherThanTheNewestInTime() {
        // Produkcja sklada [wyniki M5] + [wyniki M15] i bierze ostatni element. Wiec gdy obie
        // listy sa niepuste, "lastDiv" to zawsze ostatnia M15 — nawet jesli dywergencja M5 byla
        // nowsza. Utrwalone jako stan faktyczny; jesli intencja bylo "najnowsza w czasie", to
        // jest to blad na jedna linie.
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW);
        save(Timeframe.M15, Direction.LONG, FIXED_NOW.minusMinutes(40));
        save(Timeframe.M5, Direction.LONG, FIXED_NOW.minusMinutes(1));

        AlertToSend alert = scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW)).get(0);

        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.M15);
        assertThat(alert.getDescription()).contains("DIV M15");
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private DivergenceSignal save(Timeframe tf, Direction direction, ZonedDateTime detectedAt) {
        return divergenceRepo.save(TestFixtures.divergence(tf, direction, detectedAt));
    }
}

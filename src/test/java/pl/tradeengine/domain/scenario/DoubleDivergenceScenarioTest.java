package pl.tradeengine.domain.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.backtest.repository.InMemoryDivergenceRepository;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.PriceCandleEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleDivergenceScenarioTest {

    private InMemoryDivergenceRepository divergenceRepository;
    private DoubleDivergenceScenario scenario;

    @BeforeEach
    void setUp() {
        divergenceRepository = new InMemoryDivergenceRepository();
        scenario = new DoubleDivergenceScenario(divergenceRepository);
    }

    // The scenario derives its lookback window from ZonedDateTime.now(), so LTF fixtures
    // have to be anchored to the real clock rather than to TestFixtures.FIXED_NOW.
    private DivergenceSignal save(Timeframe tf, Direction direction, ZonedDateTime detectedAt) {
        return divergenceRepository.save(TestFixtures.divergence(tf, direction, detectedAt));
    }

    // --- H12 / D1: a single divergence is enough ---

    @Test
    void shouldEmitAlert_whenFirstEverD1Divergence() {
        DivergenceSignal signal = save(Timeframe.D1, Direction.LONG, ZonedDateTime.now());

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.D1);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getScenarioName()).isEqualTo("DOUBLE_DIVERGENCE_STRATEGY");
        assertThat(alert.getDescription()).startsWith("SingleDiv").contains("TF D1", "x1");
    }

    @Test
    void shouldEmitAlert_whenFirstEverH12Divergence() {
        DivergenceSignal signal = save(Timeframe.H12, Direction.SHORT, ZonedDateTime.now());

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).startsWith("SingleDiv").contains("TF H12");
    }

    /**
     * The HTF path must not consult history at all: a divergence older than the lookback window
     * would be invisible to the query, yet the alert still has to fire.
     */
    @Test
    void shouldEmitAlertOnD1_evenWhenHistoryIsEmptyForTheQueryWindow() {
        save(Timeframe.D1, Direction.LONG, ZonedDateTime.now().minusYears(1));
        DivergenceSignal signal = save(Timeframe.D1, Direction.LONG, ZonedDateTime.now());

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).hasSize(1);
    }

    // --- H1 / H2 / H4: still require a pair ---

    @Test
    void shouldReturnEmpty_whenSingleH1Divergence() {
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).isEmpty();
    }

    @Test
    void shouldEmitAlert_whenSecondH1DivergenceInSameDirection() {
        save(Timeframe.H1, Direction.LONG, ZonedDateTime.now().minusHours(3));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(signal));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).startsWith("DoubleDiv").contains("TF H1", "x2");
    }

    @Test
    void shouldReturnEmpty_whenEarlierH1DivergenceHasOppositeDirection() {
        save(Timeframe.H1, Direction.SHORT, ZonedDateTime.now().minusHours(3));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenEarlierH1DivergenceIsOutsideLookbackWindow() {
        save(Timeframe.H1, Direction.LONG, ZonedDateTime.now().minusDays(10));
        DivergenceSignal signal = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(signal))).isEmpty();
    }

    @Test
    void shouldIgnoreEventsOtherThanDivergence() {
        assertThat(scenario.onEvent(new PriceCandleEvent(null))).isEmpty();
    }

    // --- Luki domkniete pozniej ---

    @Test
    void shouldEmitAlert_whenSecondShortDivergenceArrivesOnH1() {
        // Caly dotychczasowy przypadek parowy stoi na LONG — filtr kierunku dziala w obie strony.
        save(Timeframe.H1, Direction.SHORT, ZonedDateTime.now().minusHours(2));
        DivergenceSignal second = save(Timeframe.H1, Direction.SHORT, ZonedDateTime.now());

        List<AlertToSend> alerts = scenario.onEvent(new DivergenceDetectedEvent(second));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDirection()).isEqualTo(Direction.SHORT);
        assertThat(alerts.get(0).getDescription()).startsWith("DoubleDiv |").contains("| x2");
    }

    @Test
    void shouldReportTheRealSignalCount_whenMoreThanTwoDivergencesAreInTheWindow() {
        // Etykieta x<n> jest liczona z rozmiaru listy, nie przypieta do 2.
        save(Timeframe.H1, Direction.LONG, ZonedDateTime.now().minusHours(3));
        save(Timeframe.H1, Direction.LONG, ZonedDateTime.now().minusHours(2));
        DivergenceSignal third = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());

        assertThat(scenario.onEvent(new DivergenceDetectedEvent(third)).get(0).getDescription())
                .contains("| x3");
    }

    @Test
    void shouldCountDivergenceSittingInsideTheThirtyFiveCandleWindow() {
        // Okno to 35 swiec liczone od teraz: na H1 to 35 godzin. 34 godziny wstecz sie lapie,
        // 36 godzin wstecz juz nie.
        save(Timeframe.H1, Direction.LONG, ZonedDateTime.now().minusHours(34));
        DivergenceSignal inside = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());
        assertThat(scenario.onEvent(new DivergenceDetectedEvent(inside))).hasSize(1);

        setUp();
        save(Timeframe.H1, Direction.LONG, ZonedDateTime.now().minusHours(36));
        DivergenceSignal outside = save(Timeframe.H1, Direction.LONG, ZonedDateTime.now());
        assertThat(scenario.onEvent(new DivergenceDetectedEvent(outside))).isEmpty();
    }

    @Test
    void shouldTreatEveryNonHtfTimeframeAsRequiringAPair() {
        // SINGLE_SIGNAL_TIMEFRAMES to tylko {H12, D1}. H2 i H4 sa w app.strategies dla tej
        // strategii, a dotad reprezentowal je wylacznie H1.
        for (Timeframe tf : new Timeframe[]{Timeframe.H2, Timeframe.H4}) {
            setUp();
            DivergenceSignal single = save(tf, Direction.LONG, ZonedDateTime.now());
            assertThat(scenario.onEvent(new DivergenceDetectedEvent(single)))
                    .as("pojedyncza dywergencja na %s", tf)
                    .isEmpty();

            DivergenceSignal second = save(tf, Direction.LONG, ZonedDateTime.now());
            assertThat(scenario.onEvent(new DivergenceDetectedEvent(second)))
                    .as("para na %s", tf)
                    .hasSize(1);
        }
    }

    @Test
    void shouldEmitSingleDivAlert_withoutQueryingHistoryOnH12() {
        // Sciezka HTF nie dotyka repozytorium — etykieta SingleDiv i licznik 1 niezaleznie od
        // tego, co w historii. Dodatkowo utrwalone: entryPrice powstaje z new BigDecimal(21.37),
        // czyli z double, wiec nie jest rowne dokladnie 21.37 i trzeba je porownywac z tolerancja.
        DivergenceSignal signal = save(Timeframe.H12, Direction.SHORT, ZonedDateTime.now());

        AlertToSend alert = scenario.onEvent(new DivergenceDetectedEvent(signal)).get(0);

        assertThat(alert.getDescription()).startsWith("SingleDiv |").contains("TF H12").contains("| x1");
        assertThat(alert.getScenarioName()).isEqualTo("DOUBLE_DIVERGENCE_STRATEGY");
        assertThat(alert.getEntryPrice()).isCloseTo(new java.math.BigDecimal("21.37"),
                org.assertj.core.data.Offset.offset(new java.math.BigDecimal("0.0000001")));
    }
}

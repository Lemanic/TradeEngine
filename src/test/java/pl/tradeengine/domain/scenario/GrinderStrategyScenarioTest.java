package pl.tradeengine.domain.scenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.StatefulSwingPointRepositoryFake;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.backtest.repository.InMemoryBiasRepository;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgFilledEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.tradeengine.TestFixtures.BTC;
import static pl.tradeengine.TestFixtures.FIXED_NOW;

class GrinderStrategyScenarioTest {

    private InMemoryFvgRepository fvgRepo;
    private InMemoryBiasRepository biasRepo;
    // StatefulSwingPointRepositoryFake, nie InMemorySwingPointRepository: ten drugi zwraca
    // z getLastSwingDirection na twardo null, wiec bramka kierunku w obu galeziach FVG bylaby
    // zawsze przepustowa i nie dalaby sie przetestowac.
    private StatefulSwingPointRepositoryFake swingRepo;
    private GrinderStrategyScenario scenario;

    @BeforeEach
    void setUp() {
        fvgRepo = new InMemoryFvgRepository();
        biasRepo = new InMemoryBiasRepository();
        swingRepo = new StatefulSwingPointRepositoryFake();

        scenario = new GrinderStrategyScenario(
                fvgRepo, biasRepo, swingRepo,
                "TEST_GRINDER",
                Timeframe.D1,
                List.of(Timeframe.H4, Timeframe.D1),
                Timeframe.H1
        );
    }

    @Test
    void shouldReturnNoAlerts_whenBiasContradictsSwingPointDirection() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent bullishSwing = TestFixtures.bullishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bullishSwing);

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldGenerateAlert_whenBiasAndPoiAndSwingPointAreAligned_longSetup() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent bullishSwing = TestFixtures.bullishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bullishSwing);

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getSymbol()).isEqualTo(BTC);
        assertThat(alert.getScenarioName()).isEqualTo("TEST_GRINDER");
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.H1);
    }

    @Test
    void shouldGenerateAlert_whenBiasAndPoiAndSwingPointAreAligned_shortSetup() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.SHORT, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent bearishSwing = TestFixtures.bearishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bearishSwing);

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getDirection()).isEqualTo(Direction.SHORT);
        assertThat(alert.getSymbol()).isEqualTo(BTC);
        assertThat(alert.getScenarioName()).isEqualTo("TEST_GRINDER");
    }

    @Test
    void shouldReturnNoAlerts_whenSwingPointTimeframeDoesNotMatchTriggerTimeframe() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        SwingPointDetectedEvent swingOnD1 = TestFixtures.swingEvent(Timeframe.D1, "SWING_LOW", FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(swingOnD1);

        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenNoPOIFvgExistsForSwingPoint() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");

        SwingPointDetectedEvent bullishSwing = TestFixtures.bullishSwingOnH1(FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(bullishSwing);

        assertThat(alerts).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Rozdzielnia zdarzen i bias bez kierunku
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnNoAlerts_whenEventTypeIsNotHandled() {
        assertThat(scenario.onEvent(new DomainEvent() {})).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenBiasIsNeutralOrUnset() {
        // resolveDirectionFromBias zwraca null dla wszystkiego poza BULLISH/BEARISH, a brak biasu
        // to NEUTRAL z repozytorium. Czyli strategia milczy, dopoki D1 nie da kierunku.
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        assertThat(scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW))).isEmpty();

        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.NEUTRAL, "test");
        assertThat(scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW))).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Sciezka swingu: okno SWING_LOOKBACK_CANDLES = 5 i POI na D1
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnNoAlerts_whenPoiFvgWasTouchedBeforeTheFiveCandleWindow() {
        // Okno to detectedAt - 5 * H1 = 5 godzin. Strefa dotknieta 6 godzin wczesniej jest stara.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(6));

        assertThat(scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldAcceptPoiFvgExactlyOnTheWindowBoundary() {
        // Warunek w produkcji to isAfter(lookback) || isEqual(lookback), czyli granica wlacznie.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(5));

        assertThat(scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW))).hasSize(1);
    }

    @Test
    void shouldAcceptPoiFvgOnTheHigherOfTheTwoPoiTimeframes() {
        // poiTimeframes to [H4, D1] — D1 dziala tak samo jak H4, co dotad nie bylo sprawdzone.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.D1, Direction.LONG, FIXED_NOW.minusHours(2));

        List<AlertToSend> alerts = scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDescription()).contains("Zone: D1 FVG");
    }

    @Test
    void shouldAcceptFilledPoiFvg_usingFilledAtWhenTouchedAtIsMissing() {
        // Filtr czasu czyta touchedAt, a gdy go nie ma — filledAt. saveFilledFvg nie ustawia
        // touchedAt, wiec ten test chodzi po tej drugiej galezi.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        assertThat(scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW))).hasSize(1);
    }

    // ─────────────────────────────────────────────────────────────
    // Galezie FVG — oznaczone w produkcji "// TURN IT OFF FOR BACKTESTING",
    // czyli zywe wylacznie w sciezce Springa i dotad bez zadnego testu.
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldGenerateAlert_whenTouchedPoiFvgAgreesWithBias() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        // Sam SWING_LOW: getLastSwingDirection zwroci null (brak SWING_HIGH), wiec bramka
        // kierunku przepuszcza, a findRecentSwings ma czym zwrocic cene wejscia.
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW.minusMinutes(30));

        List<AlertToSend> alerts = scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW));

        assertThat(alerts).hasSize(1);
        AlertToSend alert = alerts.get(0);
        assertThat(alert.getDirection()).isEqualTo(Direction.LONG);
        assertThat(alert.getTimeframe()).isEqualTo(Timeframe.H1);
        assertThat(alert.getEntryPrice()).isEqualByComparingTo("60250");
        assertThat(alert.getDescription()).contains("Late FVG touched Entry (Pre-Swing)");
    }

    @Test
    void shouldGenerateAlert_whenFilledPoiFvgAgreesWithBias_shortSetup() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        FvgZone fvg = TestFixtures.saveFilledFvg(fvgRepo, Timeframe.D1, Direction.SHORT, FIXED_NOW.minusHours(2));
        swingRepo.save(BTC, Timeframe.H1, "SWING_HIGH", new BigDecimal("61500"), FIXED_NOW.minusMinutes(10));

        List<AlertToSend> alerts = scenario.onEvent(new FvgFilledEvent(fvg, FIXED_NOW));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getDirection()).isEqualTo(Direction.SHORT);
        assertThat(alerts.get(0).getEntryPrice()).isEqualByComparingTo("61500");
        assertThat(alerts.get(0).getDescription())
                .contains("Late FVG filled Entry (Pre-Swing)")
                .contains("🔴 SHORT");
    }

    @Test
    void shouldReturnNoAlerts_whenTouchedFvgIsNotOnAPoiTimeframe() {
        // Pierwsza bramka. H1 jest triggerem, nie POI — strefa z H1 nie jest kontekstem.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H1, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        assertThat(scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenFvgDirectionContradictsBias() {
        // Druga bramka. Uwaga: przy biasie NEUTRAL tradeDirection jest null, wiec to samo
        // porownanie `!=` odrzuca strefe — w tej galezi nie ma osobnej strazy na null.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        FvgZone longFvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        assertThat(scenario.onEvent(new FvgTouchedEvent(longFvg, FIXED_NOW))).isEmpty();

        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.NEUTRAL, "test");
        assertThat(scenario.onEvent(new FvgTouchedEvent(longFvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenLastTriggerSwingPointsTheOtherWay() {
        // Trzecia bramka — ta, ktorej na InMemorySwingPointRepository nie da sie w ogole dotknac.
        // Ostatni swing na H1 to HIGH (nowszy od LOW), czyli momentum SHORT przeciw biasowi LONG.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60100"), FIXED_NOW.minusHours(3));
        swingRepo.save(BTC, Timeframe.H1, "SWING_HIGH", new BigDecimal("61200"), FIXED_NOW.minusHours(1));

        assertThat(scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenNoSwingOfTheExpectedTypeExists() {
        // Czwarta bramka: bez swingu nie ma skad wziac ceny wejscia.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        assertThat(scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldUseTheNewestSwingAsEntryPrice_andIgnoreHowOldItIs() {
        // Dwie rzeczy naraz. Po pierwsze: entry to OSTATNI swing z listy, nie pierwszy.
        // Po drugie: okno na tej sciezce to touchedAt.minusYears(1), czyli wartownik "bardzo
        // dawno" — swing z poprzedniego miesiaca nadal ustawia cene wejscia. Inaczej niz na
        // sciezce swingu, gdzie okno to 5 swiec.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("59000"), FIXED_NOW.minusMonths(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60400"), FIXED_NOW.minusDays(20));

        List<AlertToSend> alerts = scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getEntryPrice()).isEqualByComparingTo("60400");
    }

    @Test
    void shouldDescribeTheSetupWithZoneAndBiasTimeframe() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        AlertToSend alert = scenario.onEvent(new FvgTouchedEvent(fvg, FIXED_NOW)).get(0);

        // Opis jest tym, co AlertToSend.toString() wysyla na Telegram.
        assertThat(alert.getDescription())
                .contains("Pair: BTCUSDT")
                .contains("Direction: 🟢 LONG")
                .contains("Strategy: TEST_GRINDER")
                .contains("Bias: D1")
                .contains("Zone: H4 FVG [60000 - 61000]");
        assertThat(alert.toString()).isEqualTo(alert.getDescription());
    }

    @Test
    void shouldDeriveIdFromStrategyName_soTwoVariantsNeverCollide() {
        // id() to hashCode nazwy — nie stala jak w pozostalych scenariuszach, gdzie
        // DOUBLE_DIVERGENCE i FVG_KEYLEVEL_DIVERGENCE zwracaja oba 0L.
        assertThat(scenario.id()).isEqualTo((long) "TEST_GRINDER".hashCode());
        assertThat(scenario.name()).isEqualTo("TEST_GRINDER");
    }

    // ─────────────────────────────────────────────────────────────
    // handleFvgInteraction (FvgFilledEvent) ma WLASNE kopie tych samych bramek co
    // handleFvgTouchTrigger — prawie identyczne, ale to osobny kod, wiec osobne testy.
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnNoAlerts_whenFilledFvgIsNotOnAPoiTimeframe() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H1, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        assertThat(scenario.onEvent(new FvgFilledEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenFilledFvgDirectionContradictsBias() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BEARISH, "test");
        FvgZone longFvg = TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        assertThat(scenario.onEvent(new FvgFilledEvent(longFvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenFilledFvgConflictsWithLastTriggerSwing() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60100"), FIXED_NOW.minusHours(3));
        swingRepo.save(BTC, Timeframe.H1, "SWING_HIGH", new BigDecimal("61200"), FIXED_NOW.minusHours(1));

        assertThat(scenario.onEvent(new FvgFilledEvent(fvg, FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldReturnNoAlerts_whenFilledFvgHasNoSwingToPriceTheEntry() {
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveFilledFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(1));

        assertThat(scenario.onEvent(new FvgFilledEvent(fvg, FIXED_NOW))).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────
    // Strefy bez znacznikow czasu — realne, bo dokladnie takie emituje
    // WebhookProcessingService (9-argumentowy konstruktor zeruje touchedAt/filledAt)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRejectPoiFvg_whenItHasNeitherTouchedAtNorFilledAt() {
        // Sciezka swingu filtruje strefy po czasie zdarzenia; gdy oba znaczniki sa null, strefa
        // wypada z okna, choc status mowi TOUCHED.
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        fvgRepo.save(TestFixtures.fvgZone(Timeframe.H4, Direction.LONG, pl.tradeengine.domain.model.FvgStatus.TOUCHED));

        assertThat(scenario.onEvent(TestFixtures.bullishSwingOnH1(FIXED_NOW))).isEmpty();
    }

    @Test
    void shouldStillAlert_whenTouchedEventCarriesAZoneStrippedOfItsTimestamps() {
        // WebhookProcessingService emituje FvgTouchedEvent ze strefa przebudowana od nowa, czyli
        // bez touchedAt — patrz WebhookProcessingServiceEventEmissionTest. Alert powstaje, ale
        // jego opis nie ma osi czasu (timeDiffMinutes = -1).
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone strippedZone = TestFixtures.fvgZone(Timeframe.H4, Direction.LONG, pl.tradeengine.domain.model.FvgStatus.TOUCHED);
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        List<AlertToSend> alerts = scenario.onEvent(new FvgTouchedEvent(strippedZone, FIXED_NOW));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getEntryPrice()).isEqualByComparingTo("60250");
    }

    @Test
    void shouldAlert_whenZoneWasBothTouchedAndFilled() {
        // Oba znaczniki ustawione: generateAlert sklada wtedy pelna os czasu
        // "Touched ... → Filled ... → Alert NOW".
        biasRepo.updateBias(BTC, Timeframe.D1, BiasStatus.BULLISH, "test");
        FvgZone fvg = TestFixtures.saveTouchedFvg(fvgRepo, Timeframe.H4, Direction.LONG, FIXED_NOW.minusHours(3));
        fvgRepo.markFilled(fvg.getId(), FIXED_NOW.minusHours(1), FIXED_NOW.plusHours(4));
        FvgZone both = fvgRepo.findById(fvg.getId()).orElseThrow();
        swingRepo.save(BTC, Timeframe.H1, "SWING_LOW", new BigDecimal("60250"), FIXED_NOW);

        assertThat(both.getTouchedAt()).isNotNull();
        assertThat(both.getFilledAt()).isNotNull();
        assertThat(scenario.onEvent(new FvgFilledEvent(both, FIXED_NOW))).hasSize(1);
    }
}

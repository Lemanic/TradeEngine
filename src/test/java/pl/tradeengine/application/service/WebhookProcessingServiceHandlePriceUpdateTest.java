package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.tradeengine.StatefulFvgRepositoryFake;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.domain.model.AlertMode;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.BiasRepository;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.SwingPointRepository;
import pl.tradeengine.domain.scenario.ScenarioEngine;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static pl.tradeengine.TestFixtures.BTC;

@ExtendWith(MockitoExtension.class)
class WebhookProcessingServiceHandlePriceUpdateTest {

    private static final BigDecimal LOWER = new BigDecimal("60000");
    private static final BigDecimal UPPER = new BigDecimal("61000");

    private StatefulFvgRepositoryFake fvgRepo;

    @Mock private ScenarioEngine scenarioEngine;
    @Mock private AlertDispatchService alertDispatchService;
    @Mock private DivergenceRepository divergenceRepo;
    @Mock private BiasRepository biasRepo;
    @Mock private SwingPointRepository swingPointRepo;

    private WebhookProcessingService service;

    @BeforeEach
    void setUp() {
        fvgRepo = new StatefulFvgRepositoryFake();
        when(scenarioEngine.onEvent(any())).thenReturn(List.of());
        service = new WebhookProcessingService(
                scenarioEngine, alertDispatchService, fvgRepo,
                divergenceRepo, biasRepo, swingPointRepo
        );
    }

    // ─────────────────────────────────────────────────────────────
    // CREATED → TOUCHED
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldTransitionCreatedToTouched_whenLongFvgIsIntersectedButNotFilled() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.CREATED);

        // Candle dips into the zone but stays above lowerPrice (no fill).
        PriceUpdateDto dto = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60800"),  // high inside
                new BigDecimal("60200"),  // low inside but > LOWER
                new BigDecimal("60500"));

        service.handlePriceUpdate(dto);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(FvgStatus.TOUCHED);
        assertThat(after.getTouchedAt()).isNotNull();
    }

    @Test
    void shouldTransitionCreatedToTouched_whenShortFvgIsIntersectedButNotFilled() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.SHORT, LOWER, UPPER, FvgStatus.CREATED);

        // SHORT FVG zone is [60000, 61000]. Filled if high >= upperPrice (61000).
        // Use candle that intersects but stays below 61000.
        PriceUpdateDto dto = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60800"),
                new BigDecimal("60200"),
                new BigDecimal("60500"));

        service.handlePriceUpdate(dto);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(FvgStatus.TOUCHED);
    }

    // ─────────────────────────────────────────────────────────────
    // CREATED → FILLED (one-shot)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldTransitionCreatedToFilled_whenLongCandleLowReachesLowerPrice() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.CREATED);

        // LONG fill condition: low <= lowerPrice. Pierce the zone bottom.
        PriceUpdateDto dto = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60500"),
                new BigDecimal("59900"),  // below LOWER
                new BigDecimal("60000"));

        ZonedDateTime before = ZonedDateTime.now();
        service.handlePriceUpdate(dto);
        ZonedDateTime after = ZonedDateTime.now();

        FvgZone result = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(FvgStatus.FILLED);
        assertThat(result.getFilledAt()).isNotNull();
        // expiresAt = filledAt + 5 * H1.duration (5h). Allow small wall-clock slop.
        assertThat(result.getExpiresAt())
                .isAfterOrEqualTo(before.plusHours(5).minusSeconds(1))
                .isBeforeOrEqualTo(after.plusHours(5).plusSeconds(1));
    }

    @Test
    void shouldTransitionCreatedToFilled_whenShortCandleHighReachesUpperPrice() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.SHORT, LOWER, UPPER, FvgStatus.CREATED);

        // SHORT fill condition: high >= upperPrice. Pierce the zone top.
        PriceUpdateDto dto = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("61100"),  // above UPPER
                new BigDecimal("60500"),
                new BigDecimal("60900"));

        service.handlePriceUpdate(dto);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(FvgStatus.FILLED);
    }

    // ─────────────────────────────────────────────────────────────
    // TOUCHED → FILLED on a later candle
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldTransitionTouchedToFilled_whenSubsequentCandleReachesLowerPrice() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);

        PriceUpdateDto dto = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60500"),
                new BigDecimal("59800"),  // below LOWER
                new BigDecimal("60000"));

        service.handlePriceUpdate(dto);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(FvgStatus.FILLED);
    }

    // ─────────────────────────────────────────────────────────────
    // FILLED is not re-processed
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldNotReprocess_whenFvgAlreadyFilled() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.FILLED);
        ZonedDateTime originalExpires = ZonedDateTime.now().plusHours(100);
        fvg.setExpiresAt(originalExpires);
        fvg.setFilledAt(ZonedDateTime.now().minusHours(1));

        // A candle that would re-fill if processed.
        PriceUpdateDto dto = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60500"),
                new BigDecimal("59800"),
                new BigDecimal("60000"));

        service.handlePriceUpdate(dto);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(FvgStatus.FILLED);
        // expiresAt must not have been overwritten by a re-fill (would change to ~now+5h).
        assertThat(after.getExpiresAt()).isEqualTo(originalExpires);
    }

    // ─────────────────────────────────────────────────────────────
    // FILLED → CONSUMED (with "not before threshold" pre-check)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldTransitionFilledToConsumed_whenExpiresAtReached() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.FILLED);
        fvg.setFilledAt(ZonedDateTime.now().minusHours(1));

        // Phase 1: expiresAt still in the future — should stay FILLED.
        fvg.setExpiresAt(ZonedDateTime.now().plusHours(1));
        // Use an out-of-zone candle so the intersect-loop won't even look at it.
        PriceUpdateDto outOfZone = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("50000"),
                new BigDecimal("49000"),
                new BigDecimal("49500"));
        service.handlePriceUpdate(outOfZone);
        assertThat(fvgRepo.findById(fvg.getId()).orElseThrow().getStatus()).isEqualTo(FvgStatus.FILLED);

        // Phase 2: expiresAt is now in the past — consumeExpiredFilled should consume it.
        fvg.setExpiresAt(ZonedDateTime.now().minusMinutes(1));
        service.handlePriceUpdate(outOfZone);

        assertThat(fvgRepo.findById(fvg.getId()).orElseThrow().getStatus()).isEqualTo(FvgStatus.CONSUMED);
    }

    // ─────────────────────────────────────────────────────────────
    // HTF pause/resume loop
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldSetLeftZoneAt_whenTouchedHtfFvgFirstExitsZone() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);
        fvg.setAlertMode(AlertMode.ARMED);
        // leftZoneAt is null by default — first out-of-zone candle should set it.

        PriceUpdateDto outOfZone = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("50500"),
                new BigDecimal("50000"),
                new BigDecimal("50200"));

        ZonedDateTime before = ZonedDateTime.now();
        service.handlePriceUpdate(outOfZone);
        ZonedDateTime after = ZonedDateTime.now();

        FvgZone result = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(result.getLeftZoneAt())
                .isAfterOrEqualTo(before.minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(1));
        // Not paused yet — only first exit recorded.
        assertThat(result.getAlertMode()).isEqualTo(AlertMode.ARMED);
    }

    @Test
    void shouldPauseAlertMode_afterFourCandlesOutsideZone() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);
        fvg.setAlertMode(AlertMode.ARMED);

        PriceUpdateDto outOfZone = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("50500"),
                new BigDecimal("50000"),
                new BigDecimal("50200"));

        // ── Pre-check: at less than 4*H1 since leftZoneAt, must NOT pause.
        fvg.setLeftZoneAt(ZonedDateTime.now().minusHours(3));
        service.handlePriceUpdate(outOfZone);
        assertThat(fvgRepo.findById(fvg.getId()).orElseThrow().getAlertMode())
                .as("not paused before threshold")
                .isEqualTo(AlertMode.ARMED);

        // ── Now leftZoneAt is older than 4*H1 → pause condition is true.
        fvg.setLeftZoneAt(ZonedDateTime.now().minusHours(5));
        service.handlePriceUpdate(outOfZone);
        assertThat(fvgRepo.findById(fvg.getId()).orElseThrow().getAlertMode())
                .as("paused after threshold")
                .isEqualTo(AlertMode.PAUSED);
    }

    @Test
    void shouldResumeArmed_whenCandleReentersZoneAfterLeaving() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);
        fvg.setAlertMode(AlertMode.ARMED);
        fvg.setLeftZoneAt(ZonedDateTime.now().minusHours(1));

        // In-zone candle — should re-arm because leftZoneAt != null.
        PriceUpdateDto inZone = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60800"),
                new BigDecimal("60200"),
                new BigDecimal("60500"));
        service.handlePriceUpdate(inZone);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getAlertMode()).isEqualTo(AlertMode.ARMED);
        assertThat(after.getLeftZoneAt()).isNull();
    }

    @Test
    void shouldResumeArmed_whenPausedAndCandleReentersZone() {
        FvgZone fvg = TestFixtures.saveFvg(fvgRepo, Timeframe.H1, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);
        fvg.setAlertMode(AlertMode.PAUSED);
        fvg.setLeftZoneAt(null);  // simulate a stale paused FVG that we want to re-arm purely on PAUSED check

        PriceUpdateDto inZone = TestFixtures.priceUpdate(Timeframe.H1,
                new BigDecimal("60800"),
                new BigDecimal("60200"),
                new BigDecimal("60500"));
        service.handlePriceUpdate(inZone);

        FvgZone after = fvgRepo.findById(fvg.getId()).orElseThrow();
        assertThat(after.getAlertMode()).isEqualTo(AlertMode.ARMED);
    }

    @Test
    void shouldOnlyTouchHtfTimeframes_andIgnoreLowerTimeframesInPauseLoop() {
        // M15 FVG is TOUCHED but findTouchedForSymbolOnTimeframes is called with {H1,H4,D1} only.
        FvgZone m15 = TestFixtures.saveFvg(fvgRepo, Timeframe.M15, Direction.LONG, LOWER, UPPER, FvgStatus.TOUCHED);
        m15.setAlertMode(AlertMode.ARMED);
        // leftZoneAt null — if it were inspected by the loop, the loop would set it.

        PriceUpdateDto outOfZone = TestFixtures.priceUpdate(Timeframe.M15,
                new BigDecimal("50500"),
                new BigDecimal("50000"),
                new BigDecimal("50200"));

        service.handlePriceUpdate(outOfZone);

        FvgZone after = fvgRepo.findById(m15.getId()).orElseThrow();
        assertThat(after.getLeftZoneAt())
                .as("M15 must not be touched by the HTF pause/resume loop")
                .isNull();
        assertThat(after.getAlertMode()).isEqualTo(AlertMode.ARMED);
    }
}

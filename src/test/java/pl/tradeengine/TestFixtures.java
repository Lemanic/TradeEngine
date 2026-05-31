package pl.tradeengine;

import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public final class TestFixtures {

    public static final Symbol BTC = new Symbol("BTCUSDT");
    public static final ZonedDateTime FIXED_NOW = ZonedDateTime.parse("2026-05-30T12:00:00Z");

    private TestFixtures() {}

    public static FvgZone fvgZone(Timeframe tf, Direction direction, FvgStatus status) {
        return new FvgZone(
                null, BTC, tf, direction,
                new BigDecimal("60000"),
                new BigDecimal("61000"),
                1.0,
                FvgKind.FVG,
                status
        );
    }

    /**
     * Saves a CREATED FVG then marks it TOUCHED through the repository so the
     * touchedAt timestamp is preserved (InMemoryFvgRepository.save() drops it).
     */
    public static FvgZone saveTouchedFvg(InMemoryFvgRepository repo,
                                         Timeframe tf,
                                         Direction direction,
                                         ZonedDateTime touchedAt) {
        FvgZone fvg = fvgZone(tf, direction, FvgStatus.CREATED);
        FvgZone saved = repo.save(fvg);
        repo.markTouched(saved.getId(), touchedAt);
        return repo.findById(saved.getId()).orElseThrow();
    }

    public static SwingPointDetectedEvent swingEvent(Timeframe tf, String type, ZonedDateTime at) {
        return new SwingPointDetectedEvent(BTC, tf, type, new BigDecimal("60500"), at);
    }

    /** BULLISH momentum produces SWING_LOW (see WebhookProcessingService.registerSwingPoint). */
    public static SwingPointDetectedEvent bullishSwingOnH1(ZonedDateTime at) {
        return swingEvent(Timeframe.H1, "SWING_LOW", at);
    }

    /** BEARISH momentum produces SWING_HIGH. */
    public static SwingPointDetectedEvent bearishSwingOnH1(ZonedDateTime at) {
        return swingEvent(Timeframe.H1, "SWING_HIGH", at);
    }
}

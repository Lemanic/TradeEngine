package pl.tradeengine;

import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.MomentumAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.backtest.repository.InMemoryFvgRepository;
import pl.tradeengine.config.StrategyProperties;
import pl.tradeengine.domain.event.SwingPointDetectedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.FvgKind;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.FvgRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    public static FvgZone saveFilledFvg(InMemoryFvgRepository repo,
                                        Timeframe tf,
                                        Direction direction,
                                        ZonedDateTime filledAt) {
        FvgZone fvg = fvgZone(tf, direction, FvgStatus.CREATED);
        FvgZone saved = repo.save(fvg);
        repo.markFilled(saved.getId(), filledAt, filledAt.plusDays(1));
        return repo.findById(saved.getId()).orElseThrow();
    }

    public static DivergenceSignal divergence(Timeframe tf, Direction direction, ZonedDateTime detectedAt) {
        return new DivergenceSignal(null, BTC, tf, direction, 1.0, detectedAt);
    }

    /** PriceUpdateDto with BTC/H1 defaults — most handlePriceUpdate tests only care about high/low/close. */
    public static PriceUpdateDto priceUpdate(Timeframe tf, BigDecimal high, BigDecimal low, BigDecimal close) {
        return new PriceUpdateDto(BTC.code(), tf.name(), "PRICE_UPDATE", high, low, close);
    }

    /**
     * Saves an FVG in any status directly to the stateful fake repository.
     * Returns the same instance (the fake mutates in place), so callers can chain setters.
     */
    public static FvgZone saveFvg(FvgRepository repo, Timeframe tf, Direction direction,
                                  BigDecimal lower, BigDecimal upper, FvgStatus status) {
        FvgZone f = new FvgZone(null, BTC, tf, direction, lower, upper, 1.0, FvgKind.FVG, status);
        return repo.save(f);
    }

    // ─────────────────────────────────────────────────────────────
    // DTO webhookow. Mapowanie i walidacja mieszkaja w prywatnych mapDtoTo* serwisu, wiec
    // testuje sie je wylacznie przez te rekordy — same DTO sa plaskie i nie maja czego testowac.
    // ─────────────────────────────────────────────────────────────

    /** Payload FVG z TradingView. signalType decyduje o FvgKind (FVG_ vs IFVG_). */
    public static FvgAlertDto fvgAlert(Timeframe tf, String direction, String signalType, String fvgStatus) {
        return new FvgAlertDto(
                BTC.code(), tf.name(), signalType, direction,
                new BigDecimal("60000"), new BigDecimal("61000"), fvgStatus);
    }

    /** Wariant domyslny: zwykly FVG w statusie CREATED. */
    public static FvgAlertDto fvgAlert(Timeframe tf, Direction direction) {
        return fvgAlert(tf, direction.name(), "FVG_BULLISH", "CREATED");
    }

    public static DivergenceAlertDto divergenceAlert(Timeframe tf, String direction) {
        return new DivergenceAlertDto(BTC.code(), tf.name(), "DIVERGENCE", direction);
    }

    /** signal jest w payloadzie, ale WebhookProcessingService go nie czyta — liczy sie direction. */
    public static MomentumAlertDto momentumAlert(Timeframe tf, String direction) {
        return new MomentumAlertDto(BTC.code(), tf.name(), "MOMENTUM_WAVE", direction);
    }

    // ─────────────────────────────────────────────────────────────
    // Alert i konfiguracja strategii
    // ─────────────────────────────────────────────────────────────

    /** AlertToSend z wypelnionymi polami — do testow, ktore tylko przepuszczaja alert dalej. */
    public static AlertToSend alert(String scenarioName) {
        return new AlertToSend(
                BTC, Direction.LONG, scenarioName, Timeframe.H1,
                new BigDecimal("60500"), Optional.empty(), Optional.empty(),
                "TEST ALERT | " + scenarioName, FIXED_NOW);
    }

    // ─────────────────────────────────────────────────────────────
    // Fille z gieldy
    // ─────────────────────────────────────────────────────────────

    /**
     * Fill na perpie z wypelnionymi polami — do testow, ktore patrza tylko na tid i czas.
     * Testy tresci wiadomosci skladaja rekord wprost, bo tam liczy sie kazde pole.
     */
    public static TradeFill tradeFill(long tid, Instant time) {
        return new TradeFill(tid, time, "BTC", "BTC", TradeFill.Side.BUY, "Open Long",
                new BigDecimal("0.01"), new BigDecimal("100000"), BigDecimal.ZERO,
                new BigDecimal("0.5"), "USDC", false);
    }

    /** Jeden wpis z app.strategies, skladany recznie zamiast ladowania YAML-a. */
    public static StrategyProperties.StrategyConfig strategy(String name, boolean enabled, Timeframe... timeframes) {
        StrategyProperties.StrategyConfig cfg = new StrategyProperties.StrategyConfig();
        cfg.setName(name);
        cfg.setEnabled(enabled);
        cfg.setTimeframes(Arrays.asList(timeframes));
        return cfg;
    }

    public static StrategyProperties strategies(StrategyProperties.StrategyConfig... configs) {
        StrategyProperties props = new StrategyProperties();
        props.setStrategies(List.of(configs));
        return props;
    }
}

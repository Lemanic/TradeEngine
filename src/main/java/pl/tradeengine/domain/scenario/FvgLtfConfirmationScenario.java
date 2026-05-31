// src/main/java/pl/tradeengine/domain/scenario/FvgLtfConfirmationScenario.java
package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.event.FvgCreatedEvent;
import pl.tradeengine.domain.event.FvgTouchedEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.FvgRepository;
import pl.tradeengine.domain.util.PriceFormatter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FVG_LTF_CONFIRMATION strategy.
 *
 * Flow:
 *  1. HTF FVG (H1/H4) gets TOUCHED → becomes the active context.
 *  2. While FVG is ARMED (status=TOUCHED), listen for LTF confirmation:
 *     a) Divergence on M1/M3/M5/M15/H1/H4 — minimum count per timeframe:
 *        M1 → 4x (quadruple+), M3 → 2x (double+), others → 1x (single+)
 *     b) New FVG created on M5/M15/H1 in the SAME direction as HTF FVG
 *  3. On any valid LTF trigger → fire alert.
 *
 * The HTF FVG is considered "armed" as long as its status is TOUCHED (not FILLED/EXPIRED).
 * Risk management is intentionally omitted in current scope.
 */
@Component
@Slf4j
public class FvgLtfConfirmationScenario implements Scenario {

    // HTF timeframes that act as the structural context
    private static final List<Timeframe> HTF_CONTEXT_TIMEFRAMES = List.of(Timeframe.H1, Timeframe.H4);

    // LTF timeframes accepted for divergence trigger
    private static final List<Timeframe> LTF_DIVERGENCE_TIMEFRAMES =
            List.of(Timeframe.M1, Timeframe.M3, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4);

    // LTF timeframes accepted for new-FVG trigger
    private static final List<Timeframe> LTF_FVG_TIMEFRAMES =
            List.of(Timeframe.M5, Timeframe.M15, Timeframe.H1);

    /**
     * Minimum required divergence count per LTF timeframe.
     * M1 = 4x, M3 = 2x, everything else = 1x (single is enough given HTF context).
     */
    private static final Map<Timeframe, Integer> MIN_DIVERGENCE_COUNT = Map.of(
            Timeframe.M1,  4,
            Timeframe.M3,  2,
            Timeframe.M5,  1,
            Timeframe.M15, 1,
            Timeframe.H1,  1,
            Timeframe.H4,  1
    );

    /**
     * How many candles back (per TF) we look when checking divergence count.
     * This defines the "window" within which multiple divs must cluster.
     */
    private static final int DIV_LOOKBACK_CANDLES = 40;

    private final FvgRepository fvgRepository;
    private final DivergenceRepository divergenceRepository;

    public FvgLtfConfirmationScenario(FvgRepository fvgRepository,
                                      DivergenceRepository divergenceRepository) {
        this.fvgRepository = fvgRepository;
        this.divergenceRepository = divergenceRepository;
    }

    @Override
    public String name() {
        return "FVG_LTF_CONFIRMATION";
    }

    @Override
    public Long id() {
        return 3L;
    }

    // ─────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        return switch (event) {
            case FvgTouchedEvent e       -> handleFvgTouched(e);
            case DivergenceDetectedEvent e -> handleDivergence(e);
            case FvgCreatedEvent e       -> handleLtfFvgCreated(e);
            default                      -> List.of();
        };
    }

    // ─────────────────────────────────────────────────────────────
    // Handler 1: HTF FVG Touched — arm the zone, log only
    // ─────────────────────────────────────────────────────────────

    private List<AlertToSend> handleFvgTouched(FvgTouchedEvent event) {
        FvgZone fvg = event.fvgZone();

        if (!HTF_CONTEXT_TIMEFRAMES.contains(fvg.getTimeframe())) {
            return List.of();
        }

        log.info("[FVG_LTF_CONFIRMATION] HTF FVG touched → armed. Symbol: {}, TF: {}, Dir: {}, ID: {}, Zone: {}-{}",
                fvg.getSymbol().code(),
                fvg.getTimeframe(),
                fvg.getDirection(),
                fvg.getId(),
                PriceFormatter.format(fvg.getLowerPrice()),
                PriceFormatter.format(fvg.getUpperPrice()));
        return List.of();
    }

    private List<AlertToSend> handleDivergence(DivergenceDetectedEvent event) {
        DivergenceSignal signal = event.signal();
        Timeframe ltfTf = signal.getTimeframe();

        if (!LTF_DIVERGENCE_TIMEFRAMES.contains(ltfTf)) {
            return List.of();
        }

        int required = MIN_DIVERGENCE_COUNT.getOrDefault(ltfTf, 1);

        if (!meetsDivergenceCountRequirement(signal, required)) {
            log.debug("[FVG_LTF_CONFIRMATION] Divergence on {} does not meet min count ({}) — skipping. Symbol: {}, Dir: {}",
                    ltfTf, required, signal.getSymbol().code(), signal.getDirection());
            return List.of();
        }

        Optional<FvgZone> armedHtfFvg = findArmedHtfFvg(signal.getSymbol(), signal.getDirection());

        if (armedHtfFvg.isEmpty()) {
            log.debug("[FVG_LTF_CONFIRMATION] No armed HTF FVG found for Symbol: {}, Dir: {}",
                    signal.getSymbol().code(), signal.getDirection());
            return List.of();
        }

        FvgZone htfFvg = armedHtfFvg.get();
        int actualCount = countRecentDivergences(signal, DIV_LOOKBACK_CANDLES);

        log.info("[FVG_LTF_CONFIRMATION] ✅ DIVERGENCE trigger. Symbol: {}, LTF: {} ({}x div), HTF_FVG: {} {} ID:{}, Zone: {}-{}",
                signal.getSymbol().code(),
                ltfTf,
                actualCount,
                htfFvg.getTimeframe(),
                htfFvg.getDirection(),
                htfFvg.getId(),
                PriceFormatter.format(htfFvg.getLowerPrice()),
                PriceFormatter.format(htfFvg.getUpperPrice()));

        String description = buildDivergenceDescription(signal, htfFvg, actualCount);
        return List.of(buildAlert(signal.getSymbol(), signal.getDirection(), ltfTf, htfFvg, description));
    }

    private List<AlertToSend> handleLtfFvgCreated(FvgCreatedEvent event) {
        FvgZone ltfFvg = event.fvgZone();

        if (!LTF_FVG_TIMEFRAMES.contains(ltfFvg.getTimeframe())) {
            return List.of();
        }

        Optional<FvgZone> armedHtfFvg = findArmedHtfFvg(ltfFvg.getSymbol(), ltfFvg.getDirection());

        if (armedHtfFvg.isEmpty()) {
            log.debug("[FVG_LTF_CONFIRMATION] LTF FVG on {} but no armed HTF FVG. Symbol: {}, Dir: {}",
                    ltfFvg.getTimeframe(), ltfFvg.getSymbol().code(), ltfFvg.getDirection());
            return List.of();
        }

        FvgZone htfFvg = armedHtfFvg.get();

        log.info("[FVG_LTF_CONFIRMATION] ✅ LTF FVG trigger. Symbol: {}, LTF_FVG: {} {} Zone: {}-{}, HTF_FVG: {} ID:{}, Zone: {}-{}",
                ltfFvg.getSymbol().code(),
                ltfFvg.getTimeframe(),
                ltfFvg.getDirection(),
                PriceFormatter.format(ltfFvg.getLowerPrice()),
                PriceFormatter.format(ltfFvg.getUpperPrice()),
                htfFvg.getTimeframe(),
                htfFvg.getId(),
                PriceFormatter.format(htfFvg.getLowerPrice()),
                PriceFormatter.format(htfFvg.getUpperPrice()));

        String description = buildLtfFvgDescription(ltfFvg, htfFvg);
        return List.of(buildAlert(ltfFvg.getSymbol(), ltfFvg.getDirection(), ltfFvg.getTimeframe(), htfFvg, description));
    }

    /**
     * Finds the most relevant armed HTF FVG — status must be TOUCHED (price is inside/was inside).
     * FILLED zones are excluded: once filled the context is consumed.
     */
    private Optional<FvgZone> findArmedHtfFvg(Symbol symbol, Direction direction) {
        List<FvgZone> candidates = fvgRepository.findActiveForSymbolAndDirectionOnHigherTf(
                symbol,
                direction,
                List.of(FvgStatus.TOUCHED, FvgStatus.FILLED),
                HTF_CONTEXT_TIMEFRAMES
        );

        // Prefer H4 over H1 — higher TF context is stronger
        return candidates.stream()
                .sorted((a, b) -> Integer.compare(
                        HTF_CONTEXT_TIMEFRAMES.indexOf(b.getTimeframe()),
                        HTF_CONTEXT_TIMEFRAMES.indexOf(a.getTimeframe())
                ))
                .findFirst();
    }

    /**
     * Checks if the divergence signal meets the required count threshold for its timeframe.
     * Looks back DIV_LOOKBACK_CANDLES candles to count how many divs of same type occurred.
     */
    private boolean meetsDivergenceCountRequirement(DivergenceSignal signal, int required) {
        if (required <= 1) return true; // single divergence always passes (no extra DB query needed)
        return countRecentDivergences(signal, DIV_LOOKBACK_CANDLES) >= required;
    }

    private int countRecentDivergences(DivergenceSignal signal, int candleLookback) {
        ZonedDateTime lookbackFrom = signal.getDetectedAt()
                .minus(signal.getTimeframe().getDuration().multipliedBy(candleLookback));

        List<DivergenceSignal> recent = divergenceRepository.findAllByDirectionSince(
                signal.getSymbol(),
                signal.getTimeframe(),
                signal.getDirection(),
                lookbackFrom
        );
        return recent.size();
    }

    // ─────────────────────────────────────────────────────────────
    // Alert builders
    // ─────────────────────────────────────────────────────────────

    private AlertToSend buildAlert(Symbol symbol, Direction direction,
                                   Timeframe triggerTf, FvgZone htfFvg,
                                   String description) {
        return new AlertToSend(
                symbol,
                direction,
                name(),
                triggerTf,
                BigDecimal.ZERO,        // no risk mgmt in scope
                Optional.empty(),
                Optional.empty(),
                description,
                ZonedDateTime.now()
        );
    }

    private String buildDivergenceDescription(DivergenceSignal signal, FvgZone htfFvg, int divCount) {
        return String.format(
                "FVG_LTF_CONF | %s | %s | TRIGGER: DIV %s x%d | CONTEXT: %s %s %s | %s-%s",
                signal.getSymbol().code(),
                signal.getDirection(),
                signal.getTimeframe(),
                divCount,
                htfFvg.getKind(),
                htfFvg.getTimeframe(),
                htfFvg.getStatus(),
                PriceFormatter.format(htfFvg.getLowerPrice()),
                PriceFormatter.format(htfFvg.getUpperPrice())
        );
    }

    private String buildLtfFvgDescription(FvgZone ltfFvg, FvgZone htfFvg) {
        return String.format(
                "FVG_LTF_CONF | %s | %s | TRIGGER: LTF_FVG %s %s | CONTEXT: %s %s %s | %s-%s",
                ltfFvg.getSymbol().code(),
                ltfFvg.getDirection(),
                ltfFvg.getTimeframe(),
                ltfFvg.getKind(),
                htfFvg.getKind(),
                htfFvg.getTimeframe(),
                htfFvg.getStatus(),
                PriceFormatter.format(htfFvg.getLowerPrice()),
                PriceFormatter.format(htfFvg.getUpperPrice())
        );
    }
}
package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
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
import java.util.Optional;

@Component
@Slf4j
public class FvgKeyLevelDivergenceScenario implements Scenario {

    private static final int PRE_TOUCH_LOOKBACK_CANDLES = 10;
    private static final int DOUBLE_DIV_LOOKBACK_CANDLES = 35;

    private final FvgRepository fvgRepository;
    private final DivergenceRepository divergenceRepository;

    public FvgKeyLevelDivergenceScenario(FvgRepository fvgRepository, DivergenceRepository divergenceRepository) {
        this.fvgRepository = fvgRepository;
        this.divergenceRepository = divergenceRepository;
    }

    @Override
    public String name() {
        return "FVG_KEYLEVEL_DIVERGENCE_STRATEGY";
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (event instanceof DivergenceDetectedEvent divergenceEvent) {
            return handleDivergenceEvent(divergenceEvent);
        }

        if (event instanceof FvgTouchedEvent fvgTouchedEvent) {
            return handleFvgTouchedEvent(fvgTouchedEvent);
        }

        return List.of();
    }

    private List<AlertToSend> handleDivergenceEvent(DivergenceDetectedEvent divergenceEvent) {
        DivergenceSignal signal = divergenceEvent.signal();
        Symbol symbol = signal.getSymbol();
        Direction direction = signal.getDirection();

        if (signal.getTimeframe() == Timeframe.M5) {
            if (!isDoubleDivergence(signal, DOUBLE_DIV_LOOKBACK_CANDLES)) {
                log.debug("M5 divergence detected but not double - skipping. Symbol: {}, Dir: {}",
                        symbol.code(), direction);
                return List.of();
            }
        }

        List<FvgZone> candidateFvgs = fvgRepository
                .findActiveForSymbolAndDirectionOnHigherTf(
                        symbol,
                        direction,
                        List.of(FvgStatus.TOUCHED, FvgStatus.FILLED),
                        List.of(Timeframe.H1, Timeframe.H4, Timeframe.D1)
                );

        if (candidateFvgs.isEmpty()) {
            return List.of();
        }

        FvgZone fvg = candidateFvgs.get(0);

        log.info(
                "FVG_KEYLEVEL_DIVERGENCE (post/inside) detected. Symbol: {}, DivTF: {}, Dir: {}, FVG_TF: {}, FVG_Status: {}, FVG_ID: {}",
                symbol.code(),
                signal.getTimeframe(),
                direction,
                fvg.getTimeframe(),
                fvg.getStatus(),
                fvg.getId()
        );

        String description = String.format(
                "DIV+FVG | %s | %s | DIV %s | %s %s %s | SCORE %s/10 | %s-%s",
                symbol.code(),
                direction,
                signal.getTimeframe(),
                fvg.getKind(),
                fvg.getTimeframe(),
                fvg.getStatus(),
                6.9,
                PriceFormatter.format(fvg.getLowerPrice()),
                PriceFormatter.format(fvg.getUpperPrice())
        );

        AlertToSend alert = new AlertToSend(
                symbol,
                direction,
                name(),
                signal.getTimeframe(),
                new BigDecimal("21.37"),
                Optional.empty(),
                Optional.empty(),
                description
        );

        return List.of(alert);
    }

    private List<AlertToSend> handleFvgTouchedEvent(FvgTouchedEvent event) {
        FvgZone fvg = event.fvgZone();

        //TODO extract that to config
        List<Timeframe> divergenceTfs = List.of(Timeframe.M5, Timeframe.M15);

        List<DivergenceSignal> recentDivergences = divergenceTfs.stream()
                .flatMap(tf -> {
                    ZonedDateTime touchedAt = event.touchedAt();
                    ZonedDateTime lookbackTime = touchedAt.minus(
                            tf.getDuration().multipliedBy(PRE_TOUCH_LOOKBACK_CANDLES)
                    );

                    List<DivergenceSignal> signals = divergenceRepository.findAllByDirectionSince(
                            fvg.getSymbol(),
                            tf,
                            fvg.getDirection(),
                            lookbackTime
                    );
                    return signals.stream();
                })
                .toList();

        if (recentDivergences.isEmpty()) {
            return List.of();
        }

        DivergenceSignal lastDiv = recentDivergences.get(recentDivergences.size() - 1);

        log.info(
                "FVG_KEYLEVEL_DIVERGENCE (pre-touch) detected. Symbol: {}, DivTF: {}, Dir: {}, FVG_TF: {}, FVG_ID: {}, DivDetectedAt: {}",
                fvg.getSymbol().code(),
                lastDiv.getTimeframe(),
                fvg.getDirection(),
                fvg.getTimeframe(),
                fvg.getId(),
                lastDiv.getDetectedAt()
        );

        String description = String.format(
                "PRE-TOUCH | %s | %s | DIV %s | %s %s %s | %s-%s",
                fvg.getSymbol().code(),
                lastDiv.getDirection(),
                lastDiv.getTimeframe(),
                fvg.getKind(),
                fvg.getTimeframe(),
                fvg.getStatus(),
                PriceFormatter.format(fvg.getLowerPrice()),
                PriceFormatter.format(fvg.getUpperPrice())
        );

        AlertToSend alert = new AlertToSend(
                fvg.getSymbol(),
                fvg.getDirection(),
                name(),
                lastDiv.getTimeframe(),
                new BigDecimal("21.37"),
                Optional.empty(),
                Optional.empty(),
                description
        );

        return List.of(alert);
    }

    private boolean isDoubleDivergence(DivergenceSignal signal, int candleLookback) {
        ZonedDateTime lookbackTime = signal.getDetectedAt()
                .minus(signal.getTimeframe().getDuration().multipliedBy(candleLookback));

        List<DivergenceSignal> recentDivergences = divergenceRepository.findAllByDirectionSince(
                signal.getSymbol(),
                signal.getTimeframe(),
                signal.getDirection(),
                lookbackTime
        );

        return recentDivergences.size() > 1;
    }

    @Override
    public Long id() {
        return 0L;
    }
}

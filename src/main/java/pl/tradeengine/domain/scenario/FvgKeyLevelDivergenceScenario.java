package pl.tradeengine.domain.scenario;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.tradeengine.domain.event.DivergenceDetectedEvent;
import pl.tradeengine.domain.event.DomainEvent;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.FvgRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class FvgKeyLevelDivergenceScenario implements Scenario {

    private final FvgRepository fvgRepository;

    public FvgKeyLevelDivergenceScenario(FvgRepository fvgRepository) {
        this.fvgRepository = fvgRepository;
    }

    @Override
    public String name() {
        return "FVG_KEYLEVEL_DIVERGENCE_STRATEGY";
    }

    @Override
    public List<AlertToSend> onEvent(DomainEvent event) {
        if (!(event instanceof DivergenceDetectedEvent divergenceEvent)) {
            return List.of();
        }

        DivergenceSignal signal = divergenceEvent.signal();
        Symbol symbol = signal.getSymbol();
        Direction direction = signal.getDirection();

        List<FvgZone> candidateFvgs = fvgRepository
                .findActiveForSymbolAndDirectionOnHigherTf(
                        symbol,
                        direction,
                        List.of(FvgStatus.TOUCHED, FvgStatus.FILLED),
                        List.of(Timeframe.H4, Timeframe.D1)
                );

        if (candidateFvgs.isEmpty()) {
            return List.of();
        }

        FvgZone fvg = candidateFvgs.get(0);

        log.info(
                "FVG_KEYLEVEL_DIVERGENCE detected. Symbol: {}, DivTF: {}, Dir: {}, FVG_TF: {}, FVG_Status: {}, FVG_ID: {}",
                symbol.code(),
                signal.getTimeframe(),
                direction,
                fvg.getTimeframe(),
                fvg.getStatus(),
                fvg.getId()
        );

        String description = String.format(
                "Dywergencja %s na TF %s w kontekście FVG (%s, %s) na TF %s (%.4f - %.4f).",
                direction,
                signal.getTimeframe(),
                fvg.getKind(),
                fvg.getStatus(),
                fvg.getTimeframe(),
                fvg.getLowerPrice(),
                fvg.getUpperPrice()
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

    @Override
    public Long id() {
        return 0L;
    }
}

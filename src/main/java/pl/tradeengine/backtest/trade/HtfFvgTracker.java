package pl.tradeengine.backtest.trade;

import pl.tradeengine.backtest.indicators.FvgDetector;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgZone;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the bearish FVGs of higher timeframes that are still open above price — the targets a long
 * can aim at.
 *
 * <p>A zone counts as filled once any price reaches its {@code upper} edge; touching {@code lower}
 * does not remove it. Feed candles in close-time order and only after they closed: a zone is
 * created on the close of its third candle, never earlier.
 */
public class HtfFvgTracker {

    private final Map<Timeframe, FvgDetector> detectors = new EnumMap<>(Timeframe.class);
    private final List<OpenFvg> open = new ArrayList<>();

    /** A closed HTF candle: first fills older zones with its high, then may create a new one. */
    public void onHtfCandle(PriceCandle candle) {
        onPrice(candle.high().doubleValue());
        Optional<FvgZone> zone = detectors.computeIfAbsent(candle.timeframe(), tf -> new FvgDetector()).detect(candle);
        zone.filter(z -> z.getDirection() == Direction.SHORT)
                .ifPresent(z -> open.add(new OpenFvg(candle.timeframe(),
                        z.getLowerPrice().doubleValue(), z.getUpperPrice().doubleValue(), candle.closeTime())));
    }

    /** Any traded price, e.g. the high of a closed LTF candle. */
    public void onPrice(double high) {
        open.removeIf(z -> high >= z.upper());
    }

    public List<OpenFvg> openZones() {
        return List.copyOf(open);
    }
}

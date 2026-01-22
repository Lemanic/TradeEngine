package pl.tradeengine.backtest.indicators;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.*;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;

@Slf4j
public class FvgDetector {

    private final Deque<PriceCandle> buffer = new LinkedList<>();

    public Optional<FvgZone> detect(PriceCandle currentCandle) {
        buffer.addLast(currentCandle);

        if (buffer.size() > 3) {
            buffer.removeFirst();
        }

        if (buffer.size() < 3) {
            return Optional.empty();
        }

        Iterator<PriceCandle> it = buffer.iterator();
        PriceCandle c2 = it.next(); // -2
        PriceCandle c1 = it.next(); // -1
        PriceCandle c0 = it.next(); // current

        Symbol symbol = currentCandle.symbol();
        Timeframe timeframe = currentCandle.timeframe();

        // Bullish FVG: High[i-2] < Low[i]
        if (c2.high().compareTo(c0.low()) < 0) {
            log.debug("Detected Bullish FVG on {} {} at {}: [{} - {}]",
                    symbol.code(), timeframe, c0.closeTime(), c2.high(), c0.low());

            return Optional.of(new FvgZone(
                    null, symbol, timeframe, Direction.LONG,
                    c2.high(), c0.low(), null, FvgKind.FVG, FvgStatus.CREATED
            ));
        }

        // Bearish FVG: Low[i-2] > High[i]
        if (c2.low().compareTo(c0.high()) > 0) {
            log.debug("Detected Bearish FVG on {} {} at {}: [{} - {}]",
                    symbol.code(), timeframe, c0.closeTime(), c0.high(), c2.low());

            return Optional.of(new FvgZone(
                    null, symbol, timeframe, Direction.SHORT,
                    c0.high(), c2.low(), null, FvgKind.FVG, FvgStatus.CREATED
            ));
        }

        return Optional.empty();
    }
}

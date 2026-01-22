package pl.tradeengine.backtest.loader;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.PriceCandle;
import pl.tradeengine.domain.model.Timeframe;

import java.util.*;

@Slf4j
public class CandleTimeline {

    private final Map<Timeframe, Iterator<PriceCandle>> iterators = new HashMap<>();
    private final PriorityQueue<CandleClosedEvent> eventQueue;

    public CandleTimeline(Map<Timeframe, List<PriceCandle>> candlesByTf) {
        this.eventQueue = new PriorityQueue<>(
                Comparator.comparing(e -> e.candle().closeTime())
        );

        for (Map.Entry<Timeframe, List<PriceCandle>> entry : candlesByTf.entrySet()) {
            if (entry.getValue().isEmpty()) {
                log.warn("No candles for timeframe: {}", entry.getKey());
                continue;
            }

            Iterator<PriceCandle> iter = entry.getValue().iterator();
            iterators.put(entry.getKey(), iter);

            if (iter.hasNext()) {
                PriceCandle firstCandle = iter.next();
                eventQueue.offer(new CandleClosedEvent(entry.getKey(), firstCandle));
            }
        }

        log.info("Timeline initialized with {} timeframes, {} events in queue",
                iterators.size(), eventQueue.size());
    }

    public boolean hasNext() {
        return !eventQueue.isEmpty();
    }

    public CandleClosedEvent getNextEvent() {
        if (eventQueue.isEmpty()) {
            throw new NoSuchElementException("No more candle events");
        }

        CandleClosedEvent event = eventQueue.poll();

        Iterator<PriceCandle> iter = iterators.get(event.timeframe());
        if (iter != null && iter.hasNext()) {
            PriceCandle nextCandle = iter.next();
            eventQueue.offer(new CandleClosedEvent(event.timeframe(), nextCandle));
        }

        return event;
    }

    public record CandleClosedEvent(Timeframe timeframe, PriceCandle candle) {}
}

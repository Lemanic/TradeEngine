package pl.tradeengine.application.service.fill;

import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.port.fill.TradeFillRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pamiec o obsluzonych filach trzymana w mapie. Odwzorowuje adapter JPA w tym, co dla logiki
 * istotne: tid jest kluczem (drugi zapis nie dubluje), a kursorem jest NAJWIEKSZY czas filla,
 * nie ostatnio zapisany — fille przychodza z API od najnowszego.
 */
public class InMemoryTradeFillRepository implements TradeFillRepository {

    private final Map<Long, Instant> fillTimeByTid = new LinkedHashMap<>();

    @Override
    public Optional<Instant> lastFillTime() {
        return fillTimeByTid.values().stream().max(Comparator.naturalOrder());
    }

    @Override
    public boolean isNew(long tid) {
        return !fillTimeByTid.containsKey(tid);
    }

    @Override
    public void markSeen(TradeFill fill, Instant notifiedAt) {
        fillTimeByTid.put(fill.tid(), fill.time());
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        Set<Long> stale = fillTimeByTid.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        stale.forEach(fillTimeByTid::remove);
        return stale.size();
    }

    /** Tidy zapamietane jako obsluzone, w kolejnosci zapisu. */
    public Set<Long> seenTids() {
        return Set.copyOf(fillTimeByTid.keySet());
    }

    public void seed(TradeFill fill) {
        markSeen(fill, fill.time());
    }
}

package pl.tradeengine;

import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.BiasRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jak {@code backtest.repository.InMemoryBiasRepository}, ale <b>zapamietuje reason</b>, ktory tamten
 * loguje i wyrzuca. Reason jest czescia kontraktu — WebhookProcessingService sklada go jako
 * {@code "MOMENTUM_WAVE_" + timeframe.name()} i trafia on do kolumny last_reason, wiec jest
 * jedynym sladem tego, skad bias sie wzial.
 *
 * <p>Zapisuje tez <b>liczbe</b> wywolan, zeby dalo sie sprawdzic sciezke "nieznana wartosc
 * kierunku -> ciche wyjscie", gdzie cala tresc kontraktu to brak wywolania repozytorium.
 */
public class RecordingBiasRepositoryFake implements BiasRepository {

    /** Jedno wywolanie updateBias, z reasonem. */
    public record BiasUpdate(Symbol symbol, Timeframe timeframe, BiasStatus status, String reason) {}

    private final Map<Symbol, Map<Timeframe, BiasStatus>> biasMap = new ConcurrentHashMap<>();
    private final List<BiasUpdate> updates = new ArrayList<>();

    @Override
    public void updateBias(Symbol symbol, Timeframe timeframe, BiasStatus status, String reason) {
        biasMap.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(timeframe, status);
        updates.add(new BiasUpdate(symbol, timeframe, status, reason));
    }

    @Override
    public BiasStatus getBias(Symbol symbol, Timeframe timeframe) {
        return biasMap.getOrDefault(symbol, Map.of()).getOrDefault(timeframe, BiasStatus.NEUTRAL);
    }

    public List<BiasUpdate> updates() {
        return List.copyOf(updates);
    }

    public void clear() {
        updates.clear();
        biasMap.clear();
    }
}

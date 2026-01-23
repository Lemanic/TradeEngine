package pl.tradeengine.backtest.repository;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.domain.model.BiasStatus;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.BiasRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InMemoryBiasRepository implements BiasRepository {

    private final Map<Symbol, Map<Timeframe, BiasStatus>> biasMap = new ConcurrentHashMap<>();

    @Override
    public void updateBias(Symbol symbol, Timeframe tf, BiasStatus status, String reason) {
        biasMap.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>()).put(tf, status);
        log.debug("Bias updated: {} on {} -> {} (reason: {})", symbol.code(), tf, status, reason);
    }

    @Override
    public BiasStatus getBias(Symbol symbol, Timeframe tf) {
        return biasMap.getOrDefault(symbol, Map.of()).getOrDefault(tf, BiasStatus.NEUTRAL);
    }
}

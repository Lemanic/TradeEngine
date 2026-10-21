package pl.tradeengine.portfolio;

import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** A holdings source that returns a canned answer, or throws. */
public class FakeHoldingsSource implements HoldingsSource {

    private final String name;
    private final Set<String> networks;
    private final Supplier<List<Holding>> answer;

    private FakeHoldingsSource(String name, Set<String> networks, Supplier<List<Holding>> answer) {
        this.name = name;
        this.networks = networks;
        this.answer = answer;
    }

    public static FakeHoldingsSource returning(String name, List<String> networks, Holding... holdings) {
        return new FakeHoldingsSource(name, new LinkedHashSet<>(networks), () -> List.of(holdings));
    }

    public static FakeHoldingsSource failing(String name, List<String> networks, String message) {
        return new FakeHoldingsSource(name, new LinkedHashSet<>(networks), () -> {
            throw new IllegalStateException(message);
        });
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<String> networks() {
        return networks;
    }

    @Override
    public List<Holding> fetchHoldings() {
        return answer.get();
    }
}

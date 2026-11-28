package pl.tradeengine.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.tradeengine.config.StrategyProperties;
import pl.tradeengine.config.StrategyProperties.StrategyConfig;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.AlertMuteRepository;
import pl.tradeengine.domain.port.SeenSymbols;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Mutes alerts per strategy and symbol while the application runs. YAML stays the base — it says
 * which strategies exist and on which timeframes; the rules here only silence part of what an
 * enabled strategy would send. Filtering itself happens in {@link AlertDispatchService}.
 */
@Slf4j
@Service
public class AlertMuteService {

    /** How far back a symbol still counts as one the webhooks send. */
    static final Duration SEEN_SYMBOLS_WINDOW = Duration.ofDays(30);

    private static final Comparator<AlertMute> BY_SYMBOL_THEN_TIMEFRAME = Comparator
            .comparing((AlertMute mute) -> mute.symbol().code())
            .thenComparing(AlertMute::timeframe, Comparator.nullsFirst(Comparator.naturalOrder()));

    public record StrategyView(String name, boolean enabled, List<Timeframe> timeframes,
                               List<AlertMute> mutes) {}

    /** {@code symbols} also holds every muted symbol, so a rule never points outside the list. */
    public record Overview(List<Symbol> symbols, List<StrategyView> strategies) {}

    private final StrategyProperties strategyProperties;
    private final AlertMuteRepository repository;
    private final SeenSymbols seenSymbols;
    private final Clock clock;

    public AlertMuteService(StrategyProperties strategyProperties, AlertMuteRepository repository,
                            SeenSymbols seenSymbols, Clock clock) {
        this.strategyProperties = strategyProperties;
        this.repository = repository;
        this.seenSymbols = seenSymbols;
        this.clock = clock;
    }

    /** Strategies in YAML order, each with its rules. */
    public Overview overview() {
        List<AlertMute> mutes = repository.findAll();

        TreeSet<String> symbols = new TreeSet<>();
        seenSymbols.since(clock.instant().minus(SEEN_SYMBOLS_WINDOW).atZone(ZoneOffset.UTC))
                .forEach(symbol -> symbols.add(symbol.code()));
        mutes.forEach(mute -> symbols.add(mute.symbol().code()));

        List<StrategyView> strategies = strategyProperties.getStrategies().stream()
                .map(cfg -> new StrategyView(cfg.getName(), cfg.isEnabled(), cfg.getTimeframes(),
                        mutes.stream()
                                .filter(mute -> mute.strategy().equals(cfg.getName()))
                                .sorted(BY_SYMBOL_THEN_TIMEFRAME)
                                .toList()))
                .toList();

        return new Overview(symbols.stream().map(Symbol::new).toList(), strategies);
    }

    /**
     * Empty when the strategy is not in {@code app.strategies} — the caller turns that into 404.
     * Muting what is already muted returns the existing rule with its original {@code mutedAt}
     * and {@code mutedBy}.
     *
     * <p>The timeframe is deliberately NOT checked against the strategy's YAML timeframes. The
     * alert's timeframe is the scenario's choice, not the routing's: the pre-touch alert of
     * FVG_KEYLEVEL_DIVERGENCE_STRATEGY carries its divergence timeframe from a hardcoded M5/M15
     * list while YAML says [H1, H4], and LTF FVG triggers in FVG_LTF_CONFIRMATION are not routed
     * by timeframe at all. A check would make exactly those alerts unmutable per timeframe.
     */
    public Optional<AlertMute> mute(String strategy, Symbol symbol, Timeframe timeframe, String mutedBy) {
        if (!isConfigured(strategy)) {
            return Optional.empty();
        }
        Optional<AlertMute> existing = repository.findAll().stream()
                .filter(mute -> mute.sameRuleAs(strategy, symbol, timeframe))
                .findFirst();
        if (existing.isPresent()) {
            return existing;
        }

        AlertMute saved = repository.save(new AlertMute(strategy, symbol, timeframe, now(), mutedBy));
        log.info("🔇 Alerts muted: {} on {} ({}) by {}", strategy, symbol.code(), describe(timeframe),
                mutedBy == null ? "localhost" : mutedBy);
        return Optional.of(saved);
    }

    /**
     * False when the strategy is not configured. Removes only the exact rule: lifting an H1 rule
     * leaves an all-timeframes rule for the same pair in place, and vice versa.
     */
    public boolean unmute(String strategy, Symbol symbol, Timeframe timeframe) {
        if (!isConfigured(strategy)) {
            return false;
        }
        repository.delete(strategy, symbol, timeframe);
        log.info("🔔 Alerts unmuted: {} on {} ({})", strategy, symbol.code(), describe(timeframe));
        return true;
    }

    public List<String> strategyNames() {
        return strategyProperties.getStrategies().stream().map(StrategyConfig::getName).toList();
    }

    private boolean isConfigured(String strategy) {
        return strategyNames().contains(strategy);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String describe(Timeframe timeframe) {
        return timeframe == null ? "all timeframes" : timeframe.name();
    }
}

package pl.tradeengine.application.service.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.portfolio.ManualHolding;
import pl.tradeengine.domain.port.portfolio.ManualHoldingRepository;
import pl.tradeengine.domain.port.portfolio.ManualHoldingRepository.StoredAmount;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The manually maintained holdings: which ones exist comes from configuration, how much from the
 * database.
 *
 * <p>A configured amount acts as the seed. {@link #seedMissing()} runs once at startup so every
 * declared position has a row and therefore a non-null {@code updatedAt}, which the API contract
 * requires. After that the database is the source of truth and editing the configuration no longer
 * changes anything — worth knowing, because it is surprising: the YAML value is an initial value,
 * not a permanent one.
 */
public class ManualHoldingService {

    private static final Logger log = LoggerFactory.getLogger(ManualHoldingService.class);

    /** One declared position, without its amount. */
    public record Definition(String key, String source, String network, String asset, int cmcId,
                             BigDecimal configuredAmount) {}

    private final Map<String, Definition> definitions;
    private final ManualHoldingRepository repository;
    private final Clock clock;

    public ManualHoldingService(List<Definition> definitions, ManualHoldingRepository repository,
                                Clock clock) {
        Map<String, Definition> byKey = new LinkedHashMap<>();
        definitions.forEach(definition -> byKey.put(definition.key(), definition));
        this.definitions = byKey;
        this.repository = repository;
        this.clock = clock;
    }

    /** Gives every declared position a stored amount, so {@code updatedAt} is never null. */
    public void seedMissing() {
        Map<String, StoredAmount> stored = repository.findAll();
        definitions.values().stream()
                .filter(definition -> !stored.containsKey(definition.key()))
                .forEach(definition -> {
                    repository.save(definition.key(), definition.configuredAmount(), now());
                    log.info("Manual holding '{}' seeded from configuration with {} {}",
                            definition.key(), definition.configuredAmount(), definition.asset());
                });
    }

    /** Deklarowane pozycje, w kolejnosci z konfiguracji. Bez ilosci — ta jest w bazie. */
    public List<Definition> definitions() {
        return List.copyOf(definitions.values());
    }

    public List<ManualHolding> list() {
        Map<String, StoredAmount> stored = repository.findAll();
        return definitions.values().stream()
                .map(definition -> merge(definition, stored.get(definition.key())))
                .toList();
    }

    /** Empty when the key is not declared in configuration — the caller turns that into 404. */
    public Optional<ManualHolding> updateAmount(String key, BigDecimal amount) {
        Definition definition = definitions.get(key);
        if (definition == null) {
            return Optional.empty();
        }
        StoredAmount saved = repository.save(key, amount, now());
        log.info("Manual holding '{}' set to {} {}", key, amount, definition.asset());
        return Optional.of(merge(definition, saved));
    }

    /** Amount a snapshot should use: the stored one, falling back to configuration. */
    public BigDecimal amountFor(String key) {
        Definition definition = definitions.get(key);
        if (definition == null) {
            return BigDecimal.ZERO;
        }
        return repository.find(key).map(StoredAmount::amount).orElse(definition.configuredAmount());
    }

    /** Kontrakt podaje czasy z dokladnoscia do sekund — ucinamy przy zapisie, nie przy odczycie. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static ManualHolding merge(Definition definition, StoredAmount stored) {
        return new ManualHolding(
                definition.key(), definition.source(), definition.network(), definition.asset(),
                definition.cmcId(),
                stored == null ? definition.configuredAmount() : stored.amount(),
                stored == null ? null : stored.updatedAt());
    }
}

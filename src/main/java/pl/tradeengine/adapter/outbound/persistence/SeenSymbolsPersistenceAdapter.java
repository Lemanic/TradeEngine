package pl.tradeengine.adapter.outbound.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.port.SeenSymbols;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every table a webhook writes to, each read by its own time column. {@code fvg_zone} has no
 * creation time, so an FVG counts from the moment price touched it — a pair that only ever sends
 * untouched FVGs does not show up here, but can still be muted through the API by name.
 */
@Repository
public class SeenSymbolsPersistenceAdapter implements SeenSymbols {

    private static final List<String> QUERIES = List.of(
            "SELECT DISTINCT d.symbol FROM DivergenceEntity d WHERE d.detectedAt >= :since",
            "SELECT DISTINCT s.symbol FROM SwingPointEntity s WHERE s.detectedAt >= :since",
            "SELECT DISTINCT b.symbol FROM BiasEntity b WHERE b.updatedAt >= :since",
            "SELECT DISTINCT f.symbol FROM FvgEntity f WHERE f.touchedAt >= :since");

    private final EntityManager entityManager;

    public SeenSymbolsPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Symbol> since(ZonedDateTime since) {
        return QUERIES.stream()
                .flatMap(jpql -> entityManager.createQuery(jpql, String.class)
                        .setParameter("since", since)
                        .getResultList().stream())
                .filter(code -> code != null && !code.isBlank())
                .map(Symbol::new)
                .collect(Collectors.toSet());
    }
}

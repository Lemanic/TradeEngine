package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface JpaPortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, Long> {

    @Query("""
SELECT p.priceUsd FROM PortfolioPositionEntity p
 WHERE p.cmcId = :cmcId
 ORDER BY p.snapshot.takenAt DESC
""")
    List<BigDecimal> findRecentUsdPrices(@Param("cmcId") int cmcId, Pageable pageable);

    @Query("""
SELECT p FROM PortfolioPositionEntity p
 WHERE p.source = :source
   AND p.network IN :networks
   AND p.snapshot.takenAt = :takenAt
""")
    List<PortfolioPositionEntity> findPositionsOfSourceAt(@Param("source") String source,
                                                          @Param("networks") List<String> networks,
                                                          @Param("takenAt") OffsetDateTime takenAt);

    @Query("""
SELECT p.pricePln FROM PortfolioPositionEntity p
 WHERE p.cmcId = :cmcId
 ORDER BY p.snapshot.takenAt DESC
""")
    List<BigDecimal> findRecentPlnPrices(@Param("cmcId") int cmcId, Pageable pageable);
}

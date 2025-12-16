package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tradeengine.domain.model.AlertMode;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Timeframe;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public interface JpaFvgRepository extends JpaRepository<FvgEntity, Long> {

    @Query("SELECT f FROM FvgEntity f WHERE f.symbol = :symbol AND f.timeframe = :timeframe AND f.status = :status AND :price >= f.lowerPrice AND :price <= f.upperPrice")
    List<FvgEntity> findIntersectingOpenFvgs(
            @Param("symbol") String symbol,
            @Param("price") double price,
            @Param("status") FvgStatus status
    );

    @Modifying
    @Transactional
    @Query("UPDATE FvgEntity f SET f.status = :newStatus WHERE f.id = :id")
    void updateStatus(@Param("id") Long id, @Param("newStatus") FvgStatus newStatus);

    List<FvgEntity> findBySymbolAndTimeframeAndStatus(String symbol, Timeframe timeframe, FvgStatus status);


    @Query("""
SELECT f FROM FvgEntity f
 WHERE f.symbol = :symbol
   AND f.status IN ('CREATED', 'TOUCHED')
   AND f.alertMode <> 'EXPIRED'
   AND (f.upperPrice >= :low AND f.lowerPrice <= :high)
""")
    List<FvgEntity> findIntersectingForAllTimeframes(
            @Param("symbol") String symbol,
            @Param("low") BigDecimal low,
            @Param("high") BigDecimal high
    );

    @Query("""
SELECT f FROM FvgEntity f
 WHERE f.symbol = :symbol
   AND f.direction = :direction
   AND f.status IN :statuses
   AND f.timeframe IN :timeframes
   AND f.alertMode = 'ARMED'
""")
    List<FvgEntity> findBySymbolAndDirectionAndStatusInAndTimeframeIn(
            @Param("symbol") String symbol,
            @Param("direction") Direction direction,
            @Param("statuses") List<FvgStatus> statuses,
            @Param("timeframes") List<Timeframe> timeframes
    );


    @Modifying
    @Transactional
    @Query("""
   UPDATE FvgEntity f
      SET f.status = 'TOUCHED',
          f.alertMode = 'ARMED',
          f.touchedAt = :touchedAt,
          f.leftZoneAt = NULL
    WHERE f.id = :id
""")
    void markTouched(@Param("id") Long id, @Param("touchedAt") ZonedDateTime touchedAt);

    @Modifying
    @Transactional
    @Query("""
   UPDATE FvgEntity f
      SET f.leftZoneAt = :leftZoneAt
    WHERE f.id = :id
""")
    void setLeftZoneAt(@Param("id") Long id, @Param("leftZoneAt") ZonedDateTime leftZoneAt);

    @Modifying
    @Transactional
    @Query("""
   UPDATE FvgEntity f
      SET f.alertMode = :mode
    WHERE f.id = :id
""")
    void setAlertMode(@Param("id") Long id, @Param("mode") AlertMode mode);

    @Modifying
    @Transactional
    @Query("""
   UPDATE FvgEntity f
      SET f.leftZoneAt = NULL,
          f.alertMode = 'ARMED'
    WHERE f.id = :id
""")
    void resumeArmed(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("""
   UPDATE FvgEntity f
      SET f.status = 'FILLED',
          f.filledAt = :filledAt,
          f.expiresAt = :expiresAt,
          f.alertMode = 'ARMED'
    WHERE f.id = :id
""")
    void markFilled(@Param("id") Long id,
                    @Param("filledAt") ZonedDateTime filledAt,
                    @Param("expiresAt") ZonedDateTime expiresAt);

    @Modifying
    @Transactional
    @Query("""
   UPDATE FvgEntity f
      SET f.status = 'CONSUMED',
          f.alertMode = 'EXPIRED'
    WHERE f.status = 'FILLED'
      AND f.expiresAt IS NOT NULL
      AND f.expiresAt <= :now
""")
    int consumeExpiredFilled(@Param("now") ZonedDateTime now);

    @Query("""
SELECT f FROM FvgEntity f
 WHERE f.symbol = :symbol
   AND f.status = 'TOUCHED'
   AND f.timeframe IN :timeframes
   AND f.alertMode <> 'EXPIRED'
   AND f.alertMode IN ('ARMED','PAUSED')
""")
    List<FvgEntity> findTouchedHtfForSymbol(
            @Param("symbol") String symbol,
            @Param("timeframes") List<Timeframe> timeframes
    );

}

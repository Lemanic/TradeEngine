package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;

public interface JpaSwingPointRepository extends JpaRepository<SwingPointEntity, Long> {

    @Query("SELECT s FROM SwingPointEntity s WHERE s.symbol = :symbol AND s.timeframe = :timeframe AND s.type = :type AND s.detectedAt >= :since ORDER BY s.detectedAt ASC")
    List<SwingPointEntity> findRecent(
            @Param("symbol") String symbol,
            @Param("timeframe") Timeframe timeframe,
            @Param("type") String type,
            @Param("since") ZonedDateTime since
    );
}

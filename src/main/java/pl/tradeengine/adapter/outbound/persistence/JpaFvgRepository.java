package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.model.Timeframe;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface JpaFvgRepository extends JpaRepository<FvgEntity, Long> {

    @Query("SELECT f FROM FvgEntity f WHERE f.symbol = :symbol AND f.timeframe = :timeframe AND f.status = :status AND :price >= f.lowerPrice AND :price <= f.upperPrice")
    List<FvgEntity> findIntersectingOpenFvgs(
            @Param("symbol") String symbol,
            @Param("timeframe") Timeframe timeframe,
            @Param("price") double price,
            @Param("status") FvgStatus status
    );

    @Modifying
    @Transactional
    @Query("UPDATE FvgEntity f SET f.status = :newStatus WHERE f.id = :id")
    void updateStatus(@Param("id") Long id, @Param("newStatus") FvgStatus newStatus);

    List<FvgEntity> findBySymbolAndTimeframeAndStatus(String symbol, Timeframe timeframe, FvgStatus status);

}

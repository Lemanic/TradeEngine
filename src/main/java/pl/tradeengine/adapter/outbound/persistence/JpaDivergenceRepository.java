package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaDivergenceRepository extends JpaRepository<DivergenceEntity, Long> {

    Optional<DivergenceEntity> findFirstBySymbolAndTimeframeAndDetectedAtAfterOrderByDetectedAtDesc(
            String symbol, Timeframe timeframe, ZonedDateTime since
    );

    List<DivergenceEntity> findBySymbolAndTimeframeAndDirectionAndDetectedAtAfter(
            String symbol, Timeframe timeframe, Direction direction, ZonedDateTime since
    );

}

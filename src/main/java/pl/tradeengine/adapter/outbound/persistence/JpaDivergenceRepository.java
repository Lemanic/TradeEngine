package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.tradeengine.domain.model.Timeframe;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface JpaDivergenceRepository extends JpaRepository<DivergenceEntity, Long> {
    Optional<DivergenceEntity> findFirstBySymbolAndTimeframeAndDetectedAtAfterOrderByDetectedAtDesc(
            String symbol, Timeframe timeframe, ZonedDateTime since
    );
}

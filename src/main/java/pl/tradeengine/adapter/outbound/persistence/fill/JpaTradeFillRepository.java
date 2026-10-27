package pl.tradeengine.adapter.outbound.persistence.fill;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaTradeFillRepository extends JpaRepository<TradeFillEntity, Long> {

    Optional<TradeFillEntity> findTopByOrderByFillTimeDesc();

    List<TradeFillEntity> deleteByFillTimeBefore(OffsetDateTime cutoff);
}

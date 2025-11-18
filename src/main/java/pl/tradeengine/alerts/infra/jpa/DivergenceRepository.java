package pl.tradeengine.alerts.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.tradeengine.alerts.domain.Direction;

import java.time.Instant;
import java.util.List;

//@Repository
public interface DivergenceRepository  {

//    List<DivergenceEntity> findBySymbolAndTimeframeAndDirectionAndTimestampBetween(
//            String symbol, String timeframe, Direction direction, Instant from, Instant to);
}


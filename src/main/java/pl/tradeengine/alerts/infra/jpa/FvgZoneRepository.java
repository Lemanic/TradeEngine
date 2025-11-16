package pl.tradeengine.alerts.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FvgZoneRepository extends JpaRepository<FvgZoneEntity, Long> {
//    Optional<FvgZoneEntity> findBySymbolAndTimeframeAndDirectionAndFvgLowAndFvgHigh(String symbol, String timeframe, Direction direction, double fvgLow, double fvgHigh);
    List<FvgZoneEntity> findActiveBySymbol(String symbol);
    List<FvgZoneEntity> findBySymbolAndActiveIsTrue(String symbol);

    List<FvgZoneEntity> findBySymbol(String symbol);
}

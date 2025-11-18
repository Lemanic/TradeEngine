package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaFvgRepository extends JpaRepository<FvgEntity, Long> {
    // List<FvgEntity> findBySymbolAndTimeframeAndStatus(String symbol, Timeframe timeframe, FvgStatus status);
}

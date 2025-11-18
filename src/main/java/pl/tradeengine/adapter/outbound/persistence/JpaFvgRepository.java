package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// pl.tradeengine.adapter.outbound.persistence.JpaFvgRepository
public interface JpaFvgRepository extends JpaRepository<FvgEntity, Long> {
    // później możesz dodać custom metody, np.:
    // List<FvgEntity> findBySymbolAndTimeframeAndStatus(String symbol, Timeframe timeframe, FvgStatus status);
}

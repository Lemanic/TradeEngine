package pl.tradeengine.alerts.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FvgZoneRepository extends JpaRepository<FvgZoneEntity, Long> {
    // Możesz dodać metody wyszukiwania po symbolu, timeframe itd.
}

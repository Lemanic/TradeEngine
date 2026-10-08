package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaManualHoldingRepository extends JpaRepository<ManualHoldingEntity, String> {
}

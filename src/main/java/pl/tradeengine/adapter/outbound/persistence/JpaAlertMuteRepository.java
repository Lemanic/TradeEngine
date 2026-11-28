package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAlertMuteRepository extends JpaRepository<AlertMuteEntity, String> {
}

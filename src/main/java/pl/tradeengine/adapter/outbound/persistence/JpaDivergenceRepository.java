package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaDivergenceRepository extends JpaRepository<DivergenceEntity, Long> {
}

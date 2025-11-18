package pl.tradeengine.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

// pl.tradeengine.adapter.outbound.persistence.JpaDivergenceRepository
public interface JpaDivergenceRepository extends JpaRepository<DivergenceEntity, Long> {
}

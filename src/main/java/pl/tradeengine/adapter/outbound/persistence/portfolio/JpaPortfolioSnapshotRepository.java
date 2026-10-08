package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface JpaPortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshotEntity, Long> {

    /**
     * Newest snapshot with its positions in one round trip. Returns a list because a fetch join
     * yields one row per position.
     */
    @Query("""
SELECT s FROM PortfolioSnapshotEntity s
  LEFT JOIN FETCH s.positions
 WHERE s.takenAt = (SELECT MAX(s2.takenAt) FROM PortfolioSnapshotEntity s2)
""")
    List<PortfolioSnapshotEntity> findLatestWithPositions();

    Optional<PortfolioSnapshotEntity> findFirstByOrderByTakenAtDesc();

    Optional<PortfolioSnapshotEntity> findFirstByOrderByTakenAtAsc();

    Optional<PortfolioSnapshotEntity> findFirstByTakenAtLessThanEqualOrderByTakenAtDesc(OffsetDateTime takenAt);

    Optional<PortfolioSnapshotEntity> findFirstByOrderByTotalUsdDesc();

    Optional<PortfolioSnapshotEntity> findFirstByOrderByTotalPlnDesc();

    List<PortfolioSnapshotEntity> findByTakenAtGreaterThanAndTakenAtLessThanEqualOrderByTakenAtAsc(
            OffsetDateTime exclusiveFrom, OffsetDateTime inclusiveTo);

    @Transactional
    void deleteByTakenAt(OffsetDateTime takenAt);
}

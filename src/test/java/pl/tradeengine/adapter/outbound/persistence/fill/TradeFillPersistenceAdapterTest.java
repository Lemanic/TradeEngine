package pl.tradeengine.adapter.outbound.persistence.fill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.domain.model.fill.TradeFill;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprawdza to, czego fake nie sprawdzi: czy tid naprawde jest kluczem glownym (drugi zapis
 * nadpisuje, nie dubluje), czy kursorem jest najwiekszy czas filla, a nie ostatnio zapisany,
 * i czy czyszczenie usuwa tylko stare wiersze.
 */
@DataJpaTest
@Import(TradeFillPersistenceAdapter.class)
@ActiveProfiles("test")
class TradeFillPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");

    @Autowired
    private TradeFillPersistenceAdapter adapter;

    @Autowired
    private JpaTradeFillRepository jpaRepository;

    @Test
    void anEmptyTableMeansThereIsNoCursorYet() {
        assertThat(adapter.lastFillTime()).isEmpty();
        assertThat(adapter.isNew(1L)).isTrue();
    }

    @Test
    void remembersAFillAsHandled() {
        TradeFill fill = TestFixtures.tradeFill(900001L, NOW.minus(Duration.ofMinutes(5)));

        adapter.markSeen(fill, NOW);

        assertThat(adapter.isNew(900001L)).isFalse();
        assertThat(adapter.lastFillTime()).contains(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void theSameFillTwiceStaysOneRow() {
        TradeFill fill = TestFixtures.tradeFill(900002L, NOW.minus(Duration.ofMinutes(5)));

        adapter.markSeen(fill, NOW);
        adapter.markSeen(fill, NOW.plus(Duration.ofMinutes(1)));

        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    void theCursorIsTheNewestFillNotTheLastWritten() {
        adapter.markSeen(TestFixtures.tradeFill(1L, NOW.minus(Duration.ofHours(1))), NOW);
        adapter.markSeen(TestFixtures.tradeFill(2L, NOW.minus(Duration.ofHours(5))), NOW);

        assertThat(adapter.lastFillTime()).contains(NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void deletesOnlyTheRowsOlderThanTheCutoff() {
        adapter.markSeen(TestFixtures.tradeFill(1L, NOW.minus(Duration.ofDays(31))), NOW);
        adapter.markSeen(TestFixtures.tradeFill(2L, NOW.minus(Duration.ofDays(29))), NOW);

        int deleted = adapter.deleteOlderThan(NOW.minus(Duration.ofDays(30)));

        assertThat(deleted).isEqualTo(1);
        assertThat(adapter.isNew(1L)).isTrue();
        assertThat(adapter.isNew(2L)).isFalse();
    }
}

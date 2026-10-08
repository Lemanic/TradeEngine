package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.tradeengine.domain.port.portfolio.ManualHoldingRepository.StoredAmount;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprawdza to, czego fake nie sprawdzi: czy klucz tekstowy naprawde jest kluczem glownym (drugi
 * zapis nadpisuje, nie dubluje) i czy kolumna numeric(38,18) nie obcina ilosci.
 */
@DataJpaTest
@Import(ManualHoldingPersistenceAdapter.class)
@ActiveProfiles("test")
class ManualHoldingPersistenceAdapterTest {

    private static final Instant SEEDED = Instant.parse("2026-10-04T07:00:00Z");
    private static final Instant EDITED = Instant.parse("2026-10-06T11:30:00Z");

    @Autowired
    private ManualHoldingPersistenceAdapter adapter;

    @Test
    void storesAndReadsBackOneAmount() {
        adapter.save("tao-staking", new BigDecimal("11.37"), SEEDED);

        assertThat(adapter.find("tao-staking")).get().satisfies(stored -> {
            assertThat(stored.amount()).isEqualByComparingTo("11.37");
            assertThat(stored.updatedAt()).isEqualTo(SEEDED);
        });
    }

    @Test
    void anUnknownKeyIsEmptyRatherThanZero() {
        assertThat(adapter.find("nie-ma-takiego")).isEmpty();
    }

    @Test
    void savingTheSameKeyTwiceUpdatesItInPlace() {
        adapter.save("tao-staking", new BigDecimal("11.37"), SEEDED);
        StoredAmount second = adapter.save("tao-staking", new BigDecimal("12.5"), EDITED);

        assertThat(second.amount()).isEqualByComparingTo("12.5");
        assertThat(adapter.findAll()).hasSize(1);
        assertThat(adapter.find("tao-staking").orElseThrow().updatedAt()).isEqualTo(EDITED);
    }

    @Test
    void keepsEighteenDecimalPlacesOfTheAmount() {
        BigDecimal wei = new BigDecimal("0.123456789012345678");
        adapter.save("trezor-bch", wei, SEEDED);

        assertThat(adapter.find("trezor-bch").orElseThrow().amount()).isEqualByComparingTo(wei);
    }

    @Test
    void findAllIsKeyedByTheConfigurationKey() {
        adapter.save("tao-staking", new BigDecimal("11.37"), SEEDED);
        adapter.save("trb-staking", new BigDecimal("19.25"), SEEDED);

        assertThat(adapter.findAll()).containsOnlyKeys("tao-staking", "trb-staking");
    }
}

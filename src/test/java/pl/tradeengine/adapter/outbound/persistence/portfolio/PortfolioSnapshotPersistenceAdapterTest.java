package pl.tradeengine.adapter.outbound.persistence.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.model.portfolio.SnapshotHeader;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Exercises the JPA adapter against a real database: the derived queries and the JPQL cannot be
 * checked by the in-memory fake, and neither can "one hour, one snapshot".
 */
@DataJpaTest
@Import(PortfolioSnapshotPersistenceAdapter.class)
@ActiveProfiles("test")
class PortfolioSnapshotPersistenceAdapterTest {

    private static final Instant SEVEN = Instant.parse("2026-10-04T07:00:00Z");
    private static final Instant EIGHT = Instant.parse("2026-10-04T08:00:00Z");
    private static final Instant NINE = Instant.parse("2026-10-04T09:00:00Z");

    @Autowired
    private PortfolioSnapshotPersistenceAdapter adapter;

    private static PortfolioPosition position(String asset, int cmcId, String amount,
                                              String priceUsd, String valueUsd, boolean stale) {
        return new PortfolioPosition(null, "Rabby", "ethereum", asset, cmcId,
                new BigDecimal(amount), new BigDecimal(priceUsd), new BigDecimal(priceUsd).multiply(new BigDecimal("3.7")),
                new BigDecimal(valueUsd), new BigDecimal(valueUsd).multiply(new BigDecimal("3.7")), stale);
    }

    private PortfolioSnapshot store(Instant takenAt, String totalUsd, String totalPln,
                                    boolean complete, PortfolioPosition... positions) {
        return adapter.save(new PortfolioSnapshot(null, takenAt,
                new BigDecimal(totalUsd), new BigDecimal(totalPln), complete, List.of(positions)));
    }

    @Test
    void storesAndReadsBackASnapshotWithItsPositions() {
        store(SEVEN, "9001.35", "33304.99", true,
                position("ETH", 1027, "2.41", "3735.00", "9001.35", false),
                position("NPC", 27960, "1500", "0.012", "18.00", true));

        PortfolioSnapshot found = adapter.findLatest().orElseThrow();

        assertThat(found.id()).isNotNull();
        assertThat(found.takenAt()).isEqualTo(SEVEN);
        assertThat(found.totalUsd()).isEqualByComparingTo("9001.35");
        assertThat(found.complete()).isTrue();
        assertThat(found.positions())
                .extracting(PortfolioPosition::asset, PortfolioPosition::stale)
                .containsExactly(tuple("ETH", false), tuple("NPC", true));
        assertThat(found.positions().getFirst().amount()).isEqualByComparingTo("2.41");
    }

    @Test
    void replacesTheSnapshotStoredForTheSameHour() {
        store(SEVEN, "100.00", "370.00", true, position("ETH", 1027, "1", "100.00", "100.00", false));
        store(SEVEN, "200.00", "740.00", false, position("ETH", 1027, "2", "100.00", "200.00", false));

        assertThat(adapter.findHeadersAfter(SEVEN.minusSeconds(1), NINE)).hasSize(1);
        PortfolioSnapshot latest = adapter.findLatest().orElseThrow();
        assertThat(latest.totalUsd()).isEqualByComparingTo("200.00");
        assertThat(latest.complete()).isFalse();
        assertThat(latest.positions()).singleElement()
                .satisfies(position -> assertThat(position.amount()).isEqualByComparingTo("2"));
    }

    @Test
    void readsHeadersWithoutPositions() {
        store(SEVEN, "100.00", "370.00", true, position("ETH", 1027, "1", "100.00", "100.00", false));
        store(EIGHT, "110.00", "407.00", false, position("ETH", 1027, "1.1", "100.00", "110.00", false));

        assertThat(adapter.findLatestHeader()).get()
                .extracting(SnapshotHeader::takenAt, SnapshotHeader::complete)
                .containsExactly(EIGHT, false);
        // lower bound is exclusive, upper inclusive
        assertThat(adapter.findHeadersAfter(SEVEN.minusSeconds(1), EIGHT))
                .extracting(SnapshotHeader::takenAt)
                .containsExactly(SEVEN, EIGHT);
        assertThat(adapter.findHeadersAfter(SEVEN, EIGHT))
                .extracting(SnapshotHeader::takenAt)
                .containsExactly(EIGHT);
    }

    @Test
    void findsTheLastHeaderNotAfterAGivenInstant() {
        store(SEVEN, "100.00", "370.00", true);
        store(NINE, "300.00", "1110.00", true);

        assertThat(adapter.findLastHeaderNotAfter(EIGHT)).get()
                .extracting(SnapshotHeader::takenAt).isEqualTo(SEVEN);
        assertThat(adapter.findLastHeaderNotAfter(NINE)).get()
                .extracting(SnapshotHeader::takenAt).isEqualTo(NINE);
        assertThat(adapter.findLastHeaderNotAfter(SEVEN.minusSeconds(1))).isEmpty();
    }

    @Test
    void findsTheAthPerCurrencySeparately() {
        store(SEVEN, "100.00", "370.00", true);
        store(EIGHT, "120.00", "360.00", true);   // USD peak
        store(NINE, "110.00", "440.00", true);    // PLN peak

        assertThat(adapter.findAth(FiatCurrency.USD)).get()
                .extracting(SnapshotHeader::takenAt).isEqualTo(EIGHT);
        assertThat(adapter.findAth(FiatCurrency.PLN)).get()
                .extracting(SnapshotHeader::takenAt).isEqualTo(NINE);
    }

    @Test
    void findsTheFirstTakenAt() {
        assertThat(adapter.findFirstTakenAt()).isEmpty();
        store(EIGHT, "100.00", "370.00", true);
        store(SEVEN, "90.00", "333.00", true);

        assertThat(adapter.findFirstTakenAt()).contains(SEVEN);
    }

    @Test
    void findsTheMostRecentlyStoredPriceOfAnAsset() {
        store(SEVEN, "100.00", "370.00", true, position("NPC", 27960, "1000", "0.010", "10.00", false));
        store(EIGHT, "120.00", "444.00", true, position("NPC", 27960, "1000", "0.012", "12.00", false));

        assertThat(adapter.findLastKnownPrice(27960, FiatCurrency.USD)).get()
                .satisfies(price -> assertThat(price).isEqualByComparingTo("0.012"));
        assertThat(adapter.findLastKnownPrice(27960, FiatCurrency.PLN)).get()
                .satisfies(price -> assertThat(price).isEqualByComparingTo("0.0444"));
        assertThat(adapter.findLastKnownPrice(1027, FiatCurrency.USD)).isEmpty();
    }

    @Test
    void readsPositionsOfOneSourceAtAGivenHour() {
        store(SEVEN, "100.00", "370.00", true,
                position("ETH", 1027, "1", "100.00", "100.00", false));
        store(EIGHT, "200.00", "740.00", true,
                position("ETH", 1027, "2", "100.00", "200.00", false));

        assertThat(adapter.findPositionsOfSourceAt("Rabby", Set.of("ethereum"), EIGHT))
                .singleElement()
                .satisfies(found -> assertThat(found.amount()).isEqualByComparingTo("2"));
        assertThat(adapter.findPositionsOfSourceAt("Rabby", Set.of("ethereum"), SEVEN))
                .singleElement()
                .satisfies(found -> assertThat(found.amount()).isEqualByComparingTo("1"));
        // inna etykieta, inna siec, godzina bez snapshotu, pusty zbior sieci
        assertThat(adapter.findPositionsOfSourceAt("Trezor", Set.of("ethereum"), EIGHT)).isEmpty();
        assertThat(adapter.findPositionsOfSourceAt("Rabby", Set.of("bsc"), EIGHT)).isEmpty();
        assertThat(adapter.findPositionsOfSourceAt("Rabby", Set.of("ethereum"), NINE)).isEmpty();
        assertThat(adapter.findPositionsOfSourceAt("Rabby", Set.of(), EIGHT)).isEmpty();
    }

    @Test
    void readsNothingFromAnEmptyDatabase() {
        assertThat(adapter.findLatest()).isEmpty();
        assertThat(adapter.findLatestHeader()).isEmpty();
        assertThat(adapter.findAth(FiatCurrency.USD)).isEmpty();
        assertThat(adapter.findHeadersAfter(SEVEN.minusSeconds(1), NINE)).isEmpty();
    }
}

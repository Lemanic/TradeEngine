package pl.tradeengine.application.service.portfolio;

import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.portfolio.Holding;
import pl.tradeengine.domain.model.portfolio.PortfolioPosition;
import pl.tradeengine.domain.model.portfolio.PortfolioSnapshot;
import pl.tradeengine.domain.port.portfolio.HoldingsSource;
import pl.tradeengine.portfolio.FakeHoldingsSource;
import pl.tradeengine.portfolio.FakePriceProvider;
import pl.tradeengine.portfolio.InMemoryPortfolioSnapshotRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PortfolioSnapshotServiceTest {

    private static final Instant FIRST_HOUR = Instant.parse("2026-10-04T07:00:00Z");
    private static final Instant SECOND_HOUR = Instant.parse("2026-10-04T08:00:00Z");
    private static final Instant THIRD_HOUR = Instant.parse("2026-10-04T09:00:00Z");

    private static final int ETH = 1027;
    private static final int BNB = 1839;
    private static final int NPC = 27960;

    private final InMemoryPortfolioSnapshotRepository repository = new InMemoryPortfolioSnapshotRepository();

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static Clock minutesInto(Instant hour, int minutes) {
        return Clock.fixed(hour.plusSeconds(minutes * 60L), ZoneOffset.UTC);
    }

    private PortfolioSnapshotService service(Clock clock, FakePriceProvider prices, HoldingsSource... sources) {
        return new PortfolioSnapshotService(List.of(sources), prices, repository, clock,
                Duration.ofDays(7));
    }

    private static FakePriceProvider standardPrices() {
        return new FakePriceProvider()
                .with(ETH, "3735.00", "13819.50")
                .with(BNB, "601.40", "2225.18")
                .with(NPC, "0.012", "0.0444");
    }

    @Test
    void storesEveryPricedPositionAtTheTruncatedHour() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum", "arbitrum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("2.41")),
                new Holding("Rabby", "arbitrum", "ETH", ETH, new BigDecimal("2.0875")));
        FakeHoldingsSource trezor = FakeHoldingsSource.returning("Trezor", List.of("bsc"),
                new Holding("Trezor", "bsc", "BNB", BNB, new BigDecimal("7.15")));

        Optional<PortfolioSnapshot> snapshot =
                service(minutesInto(FIRST_HOUR, 42), standardPrices(), rabby, trezor).takeSnapshot();

        assertThat(snapshot).isPresent();
        PortfolioSnapshot stored = snapshot.get();
        assertThat(stored.takenAt()).isEqualTo(FIRST_HOUR);
        assertThat(stored.complete()).isTrue();
        assertThat(stored.positions())
                .extracting(PortfolioPosition::source, PortfolioPosition::network, PortfolioPosition::asset)
                .containsExactly(
                        tuple("Rabby", "arbitrum", "ETH"),
                        tuple("Rabby", "ethereum", "ETH"),
                        tuple("Trezor", "bsc", "BNB"));

        // 2.41 * 3735 + 2.0875 * 3735 + 7.15 * 601.40
        assertThat(stored.totalUsd()).isEqualByComparingTo("21098.1725");
        assertThat(stored.totalPln()).isEqualByComparingTo("78063.23825");
        assertThat(stored.positions()).allSatisfy(position -> assertThat(position.stale()).isFalse());
    }

    @Test
    void sumsHoldingsThatLandOnTheSameSourceNetworkAndAsset() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("1.5")),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("0.91")));

        PortfolioSnapshot stored =
                service(at(FIRST_HOUR), standardPrices(), rabby).takeSnapshot().orElseThrow();

        assertThat(stored.positions()).singleElement()
                .satisfies(position -> assertThat(position.amount()).isEqualByComparingTo("2.41"));
        assertThat(stored.totalUsd()).isEqualByComparingTo("9001.35");
    }

    @Test
    void carriesAmountsForwardAndMarksThemStaleWhenASourceFails() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("2.41")));
        FakeHoldingsSource trezor = FakeHoldingsSource.returning("Trezor", List.of("bsc"),
                new Holding("Trezor", "bsc", "BNB", BNB, new BigDecimal("7.15")));
        service(at(FIRST_HOUR), standardPrices(), rabby, trezor).takeSnapshot();

        FakeHoldingsSource brokenTrezor = FakeHoldingsSource.failing("Trezor", List.of("bsc"), "node down");
        FakePriceProvider cheaperBnb = standardPrices().with(BNB, "500.00", "1850.00");

        PortfolioSnapshot stored = service(at(SECOND_HOUR), cheaperBnb, rabby, brokenTrezor)
                .takeSnapshot().orElseThrow();

        assertThat(stored.complete()).isFalse();
        assertThat(stored.positions())
                .extracting(PortfolioPosition::asset, PortfolioPosition::stale)
                .containsExactly(tuple("ETH", false), tuple("BNB", true));

        PortfolioPosition bnb = stored.positions().get(1);
        // amount is yesterday's, the price is today's
        assertThat(bnb.amount()).isEqualByComparingTo("7.15");
        assertThat(bnb.priceUsd()).isEqualByComparingTo("500.00");
        assertThat(bnb.valueUsd()).isEqualByComparingTo("3575.00");
    }

    @Test
    void doesNotResurrectAPositionThatTheSourceReportedAsEmptied() {
        FakePriceProvider prices = standardPrices().with(1831, "500.00", "1850.00");
        FakeHoldingsSource holding = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"),
                new Holding("Trezor", "bitcoin-cash", "BCH", 1831, new BigDecimal("21.5")));
        service(at(FIRST_HOUR), prices, holding).takeSnapshot();

        // Godzina 2: zrodlo odpowiada i mowi, ze nic tam nie ma — sprzedane. Snapshot complete.
        FakeHoldingsSource emptied = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"));
        PortfolioSnapshot afterSelling = service(at(SECOND_HOUR), prices, emptied)
                .takeSnapshot().orElseThrow();
        assertThat(afterSelling.positions()).isEmpty();
        assertThat(afterSelling.complete()).isTrue();

        // Godzina 3: zrodlo pada. NIE wolno wskrzesic sprzedanej pozycji.
        FakeHoldingsSource broken = FakeHoldingsSource.failing("Trezor", List.of("bitcoin-cash"), "403");
        PortfolioSnapshot stored = service(at(THIRD_HOUR), prices, broken).takeSnapshot().orElseThrow();

        assertThat(stored.positions()).isEmpty();
    }

    @Test
    void looksPastAnInconclusiveSnapshotInsteadOfLosingThePosition() {
        FakePriceProvider prices = standardPrices().with(1831, "500.00", "1850.00");
        FakeHoldingsSource holding = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"),
                new Holding("Trezor", "bitcoin-cash", "BCH", 1831, new BigDecimal("21.5")));
        FakeHoldingsSource other = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("1")));
        service(at(FIRST_HOUR), prices, holding, other).takeSnapshot();

        // Godzina 2: BCH jeszcze nie bylo skonfigurowane, a Rabby padlo -> snapshot niepelny
        // i bez pozycji BCH. Taki snapshot jest NIEROZSTRZYGAJACY.
        FakeHoldingsSource brokenRabby = FakeHoldingsSource.failing("Rabby", List.of("ethereum"), "rpc down");
        PortfolioSnapshot inconclusive = new PortfolioSnapshotService(List.of(brokenRabby), prices,
                repository, at(SECOND_HOUR), Duration.ofDays(7)).takeSnapshot().orElseThrow();
        assertThat(inconclusive.complete()).isFalse();
        assertThat(inconclusive.positions()).noneSatisfy(
                position -> assertThat(position.asset()).isEqualTo("BCH"));

        // Godzina 3: BCH pada. Najnowszy snapshot nie ma BCH, ale byl niepelny — trzeba siegnac glebiej.
        FakeHoldingsSource brokenBch = FakeHoldingsSource.failing("Trezor", List.of("bitcoin-cash"), "403");
        PortfolioSnapshot stored = service(at(THIRD_HOUR), standardPrices().with(1831, "520.00", "1924.00"),
                brokenBch).takeSnapshot().orElseThrow();

        assertThat(stored.positions()).singleElement().satisfies(position -> {
            assertThat(position.asset()).isEqualTo("BCH");
            assertThat(position.amount()).isEqualByComparingTo("21.5");     // ilosc z godziny 1
            assertThat(position.priceUsd()).isEqualByComparingTo("520.00"); // cena biezaca
            assertThat(position.valueUsd()).isEqualByComparingTo("11180.00");
            assertThat(position.stale()).isTrue();
        });
        assertThat(stored.complete()).isFalse();
    }

    @Test
    void refusesToCarryAnAmountOlderThanTheWindow() {
        FakeHoldingsSource working = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"),
                new Holding("Trezor", "bitcoin-cash", "BCH", 1831, new BigDecimal("21.5")));
        service(at(FIRST_HOUR), standardPrices().with(1831, "500.00", "1850.00"), working).takeSnapshot();

        FakeHoldingsSource broken = FakeHoldingsSource.failing("Trezor", List.of("bitcoin-cash"), "403");
        PortfolioSnapshot stored = new PortfolioSnapshotService(List.of(broken),
                standardPrices().with(1831, "520.00", "1924.00"), repository,
                at(FIRST_HOUR.plus(Duration.ofDays(8))), Duration.ofDays(7))
                .takeSnapshot().orElseThrow();

        // osiem dni to za duzo: zla liczba jest gorsza niz uczciwa dziura
        assertThat(stored.positions()).isEmpty();
        assertThat(stored.complete()).isFalse();
    }

    @Test
    void keepsCarryingForwardAcrossConsecutiveFailures() {
        FakePriceProvider prices = standardPrices().with(1831, "500.00", "1850.00");
        FakeHoldingsSource working = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"),
                new Holding("Trezor", "bitcoin-cash", "BCH", 1831, new BigDecimal("21.5")));
        service(at(FIRST_HOUR), prices, working).takeSnapshot();

        FakeHoldingsSource broken = FakeHoldingsSource.failing("Trezor", List.of("bitcoin-cash"), "403");
        for (Instant hour : List.of(SECOND_HOUR, THIRD_HOUR, FIRST_HOUR.plus(Duration.ofHours(3)))) {
            PortfolioSnapshot stored = service(at(hour), prices, broken).takeSnapshot().orElseThrow();
            assertThat(stored.positions()).as("godzina %s", hour).singleElement()
                    .satisfies(position -> {
                        assertThat(position.amount()).isEqualByComparingTo("21.5");
                        assertThat(position.stale()).isTrue();
                    });
        }
    }

    @Test
    void carriesForwardOnlyTheNetworksOfTheFailedSourceWhenALabelIsSplit() {
        FakeHoldingsSource trezorEvm = FakeHoldingsSource.returning("Trezor", List.of("bsc"),
                new Holding("Trezor", "bsc", "BNB", BNB, new BigDecimal("7.15")));
        FakeHoldingsSource trezorOther = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"),
                new Holding("Trezor", "bitcoin-cash", "BCH", 1831, new BigDecimal("21.5")));
        FakePriceProvider prices = standardPrices().with(1831, "511.63", "1893.03");
        service(at(FIRST_HOUR), prices, trezorEvm, trezorOther).takeSnapshot();

        FakeHoldingsSource brokenEvm = FakeHoldingsSource.failing("Trezor", List.of("bsc"), "node down");
        FakeHoldingsSource freshOther = FakeHoldingsSource.returning("Trezor", List.of("bitcoin-cash"),
                new Holding("Trezor", "bitcoin-cash", "BCH", 1831, new BigDecimal("22")));

        PortfolioSnapshot stored = service(at(SECOND_HOUR), prices, brokenEvm, freshOther)
                .takeSnapshot().orElseThrow();

        assertThat(stored.positions())
                .extracting(PortfolioPosition::network, PortfolioPosition::asset,
                        position -> position.amount().stripTrailingZeros().toPlainString(),
                        PortfolioPosition::stale)
                .containsExactly(
                        tuple("bitcoin-cash", "BCH", "22", false),
                        tuple("bsc", "BNB", "7.15", true));
    }

    @Test
    void reusesTheLastKnownPriceWhenAnAssetHasNoFreshQuote() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "NPC", NPC, new BigDecimal("1500")));
        service(at(FIRST_HOUR), standardPrices(), rabby).takeSnapshot();

        FakePriceProvider withoutNpc = new FakePriceProvider().with(ETH, "3735.00", "13819.50");

        PortfolioSnapshot stored =
                service(at(SECOND_HOUR), withoutNpc, rabby).takeSnapshot().orElseThrow();

        assertThat(stored.positions()).singleElement().satisfies(position -> {
            assertThat(position.priceUsd()).isEqualByComparingTo("0.012");
            assertThat(position.pricePln()).isEqualByComparingTo("0.0444");
            assertThat(position.valueUsd()).isEqualByComparingTo("18.00");
            assertThat(position.stale()).isTrue();
        });
        // a stale price does not make the snapshot incomplete — every source did answer
        assertThat(stored.complete()).isTrue();
    }

    @Test
    void dropsAPositionWithNeitherAFreshNorAStoredPrice() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("2.41")),
                new Holding("Rabby", "ethereum", "NPC", NPC, new BigDecimal("1500")));
        FakePriceProvider withoutNpc = new FakePriceProvider().with(ETH, "3735.00", "13819.50");

        PortfolioSnapshot stored =
                service(at(FIRST_HOUR), withoutNpc, rabby).takeSnapshot().orElseThrow();

        assertThat(stored.positions()).extracting(PortfolioPosition::asset).containsExactly("ETH");
        assertThat(stored.totalUsd()).isEqualByComparingTo("9001.35");
        assertThat(stored.complete()).isFalse();
    }

    @Test
    void writesNothingWhenPricesCannotBeFetched() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("2.41")));
        FakePriceProvider broken = new FakePriceProvider().failingWith("CoinMarketCap rate limit reached (429)");

        assertThat(service(at(FIRST_HOUR), broken, rabby).takeSnapshot()).isEmpty();
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void writesNothingWhenNoSourceIsConfigured() {
        assertThat(service(at(FIRST_HOUR), standardPrices()).takeSnapshot()).isEmpty();
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void replacesTheSnapshotAlreadyStoredForTheSameHour() {
        FakeHoldingsSource first = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("2.41")));
        service(minutesInto(FIRST_HOUR, 2), standardPrices(), first).takeSnapshot();

        FakeHoldingsSource afterTopUp = FakeHoldingsSource.returning("Rabby", List.of("ethereum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("3.00")));
        PortfolioSnapshot replaced = service(minutesInto(FIRST_HOUR, 57), standardPrices(), afterTopUp)
                .takeSnapshot().orElseThrow();

        assertThat(repository.all()).hasSize(1);
        assertThat(replaced.takenAt()).isEqualTo(FIRST_HOUR);
        assertThat(replaced.positions()).singleElement()
                .satisfies(position -> assertThat(position.amount()).isEqualByComparingTo("3.00"));
    }

    @Test
    void asksForThePriceOfEveryHeldAssetExactlyOnce() {
        FakeHoldingsSource rabby = FakeHoldingsSource.returning("Rabby", List.of("ethereum", "arbitrum"),
                new Holding("Rabby", "ethereum", "ETH", ETH, new BigDecimal("1")),
                new Holding("Rabby", "arbitrum", "ETH", ETH, new BigDecimal("1")),
                new Holding("Rabby", "ethereum", "NPC", NPC, new BigDecimal("10")));
        FakePriceProvider prices = standardPrices();

        service(at(FIRST_HOUR), prices, rabby).takeSnapshot();

        assertThat(prices.lastRequestedIds()).containsExactly(ETH, NPC);
    }
}

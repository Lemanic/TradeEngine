package pl.tradeengine.application.service.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.domain.model.portfolio.ManualHolding;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ManualHoldingServiceTest {

    private static final Instant SEEDED_AT = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant EDITED_AT = Instant.parse("2026-10-06T11:30:00Z");

    private final InMemoryManualHoldingRepository repository = new InMemoryManualHoldingRepository();
    private Instant now = SEEDED_AT;
    private ManualHoldingService service;

    private static ManualHoldingService.Definition bch(String amount) {
        return new ManualHoldingService.Definition("trezor-bch", "Trezor", "bitcoin-cash", "BCH",
                1831, new BigDecimal(amount));
    }

    private static ManualHoldingService.Definition tao(String amount) {
        return new ManualHoldingService.Definition("tao-staking", "Bittensor", "bittensor", "TAO",
                22974, new BigDecimal(amount));
    }

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        service = new ManualHoldingService(List.of(bch("0.744"), tao("11.37")), repository, clock);
    }

    @Test
    void seedsEveryDeclaredPositionSoUpdatedAtIsNeverNull() {
        service.seedMissing();

        assertThat(service.list()).extracting(ManualHolding::key, ManualHolding::updatedAt)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("trezor-bch", SEEDED_AT),
                        org.assertj.core.api.Assertions.tuple("tao-staking", SEEDED_AT));
        assertThat(service.list()).extracting(ManualHolding::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.744"), new BigDecimal("11.37"));
    }

    /** Ziarno jest ziarnem: drugi start nie moze nadpisac tego, co wpisal czlowiek. */
    @Test
    void seedingTwiceDoesNotOverwriteAnEditedAmount() {
        service.seedMissing();
        now = EDITED_AT;
        service.updateAmount("tao-staking", new BigDecimal("12.5"));

        service.seedMissing();

        assertThat(service.amountFor("tao-staking")).isEqualByComparingTo("12.5");
        assertThat(repository.find("tao-staking").orElseThrow().updatedAt()).isEqualTo(EDITED_AT);
    }

    @Test
    void updateStampsTheMomentAndReturnsTheMergedPosition() {
        service.seedMissing();
        now = EDITED_AT;

        Optional<ManualHolding> updated = service.updateAmount("trezor-bch", new BigDecimal("0.81"));

        assertThat(updated).get().satisfies(holding -> {
            assertThat(holding.key()).isEqualTo("trezor-bch");
            assertThat(holding.source()).isEqualTo("Trezor");
            assertThat(holding.network()).isEqualTo("bitcoin-cash");
            assertThat(holding.asset()).isEqualTo("BCH");
            assertThat(holding.cmcId()).isEqualTo(1831);
            assertThat(holding.amount()).isEqualByComparingTo("0.81");
            assertThat(holding.updatedAt()).isEqualTo(EDITED_AT);
        });
        assertThat(service.amountFor("trezor-bch")).isEqualByComparingTo("0.81");
    }

    @Test
    void zeroIsAValidAmountAndKeepsThePositionDeclared() {
        service.seedMissing();

        assertThat(service.updateAmount("trezor-bch", BigDecimal.ZERO)).isPresent();

        assertThat(service.amountFor("trezor-bch")).isEqualByComparingTo("0");
        assertThat(service.list()).extracting(ManualHolding::key).contains("trezor-bch");
    }

    @Test
    void anUndeclaredKeyIsEmptyAndStoresNothing() {
        service.seedMissing();

        assertThat(service.updateAmount("gold-bar", new BigDecimal("1"))).isEmpty();

        assertThat(repository.size()).isEqualTo(2);
        assertThat(service.amountFor("gold-bar")).isEqualByComparingTo("0");
    }

    /** Przed zasianiem snapshot i tak musi miec liczbe — bierze ja z konfiguracji. */
    @Test
    void fallsBackToTheConfiguredAmountBeforeSeeding() {
        assertThat(service.amountFor("tao-staking")).isEqualByComparingTo("11.37");
        assertThat(service.list()).extracting(ManualHolding::updatedAt).containsOnlyNulls();
    }

    @Test
    void definitionsKeepConfigurationOrderAndCarryNoAmount() {
        assertThat(service.definitions()).extracting(ManualHoldingService.Definition::key)
                .containsExactly("trezor-bch", "tao-staking");
    }
}

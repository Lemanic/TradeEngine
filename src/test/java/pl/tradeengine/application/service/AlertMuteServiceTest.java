package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.application.service.AlertMuteService.Overview;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMuteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-28T09:15:42.123Z");
    private static final Instant EARLIER = Instant.parse("2026-11-20T08:00:00Z");
    private static final Symbol TAO = new Symbol("TAOUSDT");
    private static final Symbol NQ = new Symbol("NQ1!");

    private InMemoryAlertMuteRepository repository;
    private FakeSeenSymbols seenSymbols;
    private AlertMuteService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAlertMuteRepository();
        seenSymbols = new FakeSeenSymbols("TAOUSDT", "BTCUSDT");
        service = new AlertMuteService(
                TestFixtures.strategies(
                        TestFixtures.strategy("DOUBLE_DIVERGENCE_STRATEGY", true, Timeframe.H1, Timeframe.H4),
                        TestFixtures.strategy("GRINDER_SWING_D1_H1", true, Timeframe.H1),
                        TestFixtures.strategy("FVG_LTF_CONFIRMATION", false, Timeframe.M5)),
                repository, seenSymbols, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void overviewListsStrategiesInYamlOrder_withTheirFlagsAndTimeframes() {
        Overview overview = service.overview();

        assertThat(overview.strategies())
                .extracting(AlertMuteService.StrategyView::name)
                .containsExactly("DOUBLE_DIVERGENCE_STRATEGY", "GRINDER_SWING_D1_H1", "FVG_LTF_CONFIRMATION");
        assertThat(overview.strategies().get(0).timeframes()).containsExactly(Timeframe.H1, Timeframe.H4);
        assertThat(overview.strategies().get(2).enabled()).isFalse();
    }

    @Test
    void symbolsAreTheSeenOnesSortedAlphabetically_andAskedForThirtyDaysBack() {
        Overview overview = service.overview();

        assertThat(overview.symbols()).extracting(Symbol::code).containsExactly("BTCUSDT", "TAOUSDT");
        assertThat(seenSymbols.lastAskedSince())
                .isEqualTo(ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30));
    }

    @Test
    void aMutedSymbolStaysInTheList_evenAfterItStoppedArriving() {
        // Inaczej regula zostalaby w bazie bez wiersza w UI — i nie daloby sie jej zdjac klikiem.
        repository.save(new AlertMute("GRINDER_SWING_D1_H1", NQ, null, EARLIER, null));

        assertThat(service.overview().symbols()).extracting(Symbol::code)
                .containsExactly("BTCUSDT", "NQ1!", "TAOUSDT");
    }

    @Test
    void eachStrategyGetsOnlyItsOwnRules_allTimeframesFirst() {
        repository.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H4, EARLIER, null));
        repository.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, null, EARLIER, null));
        repository.save(new AlertMute("GRINDER_SWING_D1_H1", NQ, null, EARLIER, null));

        Overview overview = service.overview();

        assertThat(overview.strategies().get(0).mutes())
                .extracting(AlertMute::timeframe)
                .containsExactly(null, Timeframe.H4);
        assertThat(overview.strategies().get(1).mutes()).extracting(AlertMute::symbol).containsExactly(NQ);
        assertThat(overview.strategies().get(2).mutes()).isEmpty();
    }

    @Test
    void muteStoresTheRuleWithTheTimeTruncatedToSeconds() {
        AlertMute mute = service.mute("GRINDER_SWING_D1_H1", TAO, null, "owner@example.com").orElseThrow();

        assertThat(mute.mutedAt()).isEqualTo(Instant.parse("2026-11-28T09:15:42Z"));
        assertThat(mute.mutedBy()).isEqualTo("owner@example.com");
        assertThat(repository.findAll()).containsExactly(mute);
    }

    @Test
    void mutingTwiceKeepsTheOriginalRule() {
        // UI moze wyslac PUT dwa razy (podwojny klik, retry) — mutedAt ma mowic, od kiedy jest cisza,
        // a mutedBy, kto ja wlaczyl, nie kto kliknal drugi raz.
        repository.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, EARLIER, "first@example.com"));

        AlertMute again = service.mute("GRINDER_SWING_D1_H1", TAO, null, "second@example.com").orElseThrow();

        assertThat(again.mutedAt()).isEqualTo(EARLIER);
        assertThat(again.mutedBy()).isEqualTo("first@example.com");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void aStrategyOutsideTheYamlCannotBeMuted() {
        // Literowka w nazwie zapisalaby regule, ktora nigdy niczego nie wyciszy.
        assertThat(service.mute("GRINDER_SWING", TAO, null, null)).isEmpty();
        assertThat(service.unmute("GRINDER_SWING", TAO, null)).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void aDisabledStrategyCanBeMuted_soTheRuleIsReadyWhenItIsSwitchedOn() {
        assertThat(service.mute("FVG_LTF_CONFIRMATION", TAO, Timeframe.M5, null)).isPresent();
    }

    @Test
    void aTimeframeOutsideTheYamlListCanBeMuted() {
        // Pre-touch alert FVG_KEYLEVEL niesie M5/M15 z zahardkodowanej listy, choc YAML mowi [H1, H4] —
        // walidacja wzgledem YAML-a zrobilaby z niego alert niewyciszalny per interwal.
        assertThat(service.mute("GRINDER_SWING_D1_H1", TAO, Timeframe.M15, null)).isPresent();
    }

    @Test
    void unmuteRemovesOnlyTheExactRule() {
        repository.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, null, EARLIER, null));
        repository.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1, EARLIER, null));

        assertThat(service.unmute("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1)).isTrue();

        assertThat(repository.findAll()).extracting(AlertMute::timeframe).containsExactly((Timeframe) null);
    }

    @Test
    void unmutingWhatIsNotMutedIsFine() {
        assertThat(service.unmute("GRINDER_SWING_D1_H1", TAO, null)).isTrue();
    }
}

package pl.tradeengine.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.FakeAlertPublisher;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.AlertToSend;
import pl.tradeengine.domain.model.Direction;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.AlertMuteRepository;
import pl.tradeengine.domain.port.AlertPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jedyna brama miedzy scenariuszami a publisherem. Kazdy alert w systemie przez nia przechodzi,
 * wiec cicha zmiana tutaj wycisza cala aplikacje — a od kiedy filtruje wyciszenia, to jest tez
 * jedyne miejsce, w ktorym wyciszenie w ogole dziala.
 */
class AlertDispatchServiceTest {

    private static final Instant MUTED_AT = Instant.parse("2026-11-28T09:00:00Z");
    private static final Symbol TAO = new Symbol("TAOUSDT");

    private FakeAlertPublisher publisher;
    private InMemoryAlertMuteRepository mutes;
    private AlertDispatchService service;

    @BeforeEach
    void setUp() {
        publisher = new FakeAlertPublisher();
        mutes = new InMemoryAlertMuteRepository();
        service = new AlertDispatchService(publisher, mutes);
    }

    @Test
    void shouldNotTouchThePublisher_whenAlertListIsNull() {
        service.dispatch(null);

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void shouldNotTouchThePublisher_whenAlertListIsEmpty() {
        // Wazne, bo ScenarioEngine zwraca pusta liste przy kazdym zdarzeniu, ktorego zaden
        // scenariusz nie obsluguje — czyli przy wiekszosci swiec.
        service.dispatch(List.of());

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void shouldPassTheWholeListInOneCall_whenAlertsArePresent() {
        AlertToSend first = TestFixtures.alert("FIRST");
        AlertToSend second = TestFixtures.alert("SECOND");

        service.dispatch(List.of(first, second));

        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().get(0)).containsExactly(first, second);
    }

    @Test
    void shouldPropagatePublisherFailure_becauseThereIsNoTryCatchHere() {
        // Utrwalone swiadomie: awaria Telegrama wychodzi az do wolajacego, czyli do
        // WebhookProcessingService, a tam dalej do catch-all w kontrolerze (400 dla TradingView).
        AlertPublisher throwing = alerts -> {
            throw new IllegalStateException("telegram down");
        };

        assertThatThrownBy(() -> new AlertDispatchService(throwing, mutes).dispatch(List.of(TestFixtures.alert("X"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("telegram down");
    }

    // ─────────────────────────────────────────────────────────────
    // Wyciszenia
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldDropOnlyTheMutedAlert_andKeepTheRestInOneCall() {
        AlertToSend muted = alert("GRINDER_SWING_D1_H1", TAO, Timeframe.H1);
        AlertToSend sameStrategyOtherPair = alert("GRINDER_SWING_D1_H1", TestFixtures.BTC, Timeframe.H1);
        AlertToSend samePairOtherStrategy = alert("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1);
        mutes.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, MUTED_AT, null));

        service.dispatch(List.of(muted, sameStrategyOtherPair, samePairOtherStrategy));

        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().get(0)).containsExactly(sameStrategyOtherPair, samePairOtherStrategy);
    }

    @Test
    void shouldNotTouchThePublisher_whenEveryAlertIsMuted() {
        // Pusta lista do publishera to pusta wiadomosc na Telegramie — wiec nie wolamy go wcale.
        mutes.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, MUTED_AT, null));

        service.dispatch(List.of(alert("GRINDER_SWING_D1_H1", TAO, Timeframe.H1)));

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void aTimeframeRuleMutesThatTimeframeOnly() {
        AlertToSend onH1 = alert("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1);
        AlertToSend onH4 = alert("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H4);
        mutes.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1, MUTED_AT, null));

        service.dispatch(List.of(onH1, onH4));

        assertThat(publisher.allAlerts()).containsExactly(onH4);
    }

    @Test
    void aRuleWithoutTimeframeMutesEveryTimeframe() {
        mutes.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, null, MUTED_AT, null));

        service.dispatch(List.of(
                alert("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.M15),
                alert("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.D1)));

        assertThat(publisher.published()).isEmpty();
    }

    @Test
    void symbolMustMatchExactly_soAPrefixDoesNotMute() {
        // Symbol to wolny string z webhooka — "TAO" i "TAOUSDT" to dwie rozne pary.
        AlertToSend alert = alert("GRINDER_SWING_D1_H1", TAO, Timeframe.H1);
        mutes.save(new AlertMute("GRINDER_SWING_D1_H1", new Symbol("TAO"), null, MUTED_AT, null));

        service.dispatch(List.of(alert));

        assertThat(publisher.allAlerts()).containsExactly(alert);
    }

    @Test
    void anUnmuteAppliesToTheVeryNextDispatch_withoutRebuildingTheService() {
        mutes.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, MUTED_AT, null));
        service.dispatch(List.of(alert("GRINDER_SWING_D1_H1", TAO, Timeframe.H1)));

        mutes.delete("GRINDER_SWING_D1_H1", TAO, null);
        AlertToSend afterUnmute = alert("GRINDER_SWING_D1_H1", TAO, Timeframe.H1);
        service.dispatch(List.of(afterUnmute));

        assertThat(publisher.allAlerts()).containsExactly(afterUnmute);
    }

    @Test
    void shouldSendEverything_whenTheRulesCannotBeRead() {
        // Zbedny alert jest lepszy niz przegapiony: awaria bazy nie moze wyciszyc calej aplikacji.
        AlertMuteRepository broken = new InMemoryAlertMuteRepository() {
            @Override
            public List<AlertMute> findAll() {
                throw new IllegalStateException("db down");
            }
        };
        AlertToSend alert = alert("GRINDER_SWING_D1_H1", TAO, Timeframe.H1);

        new AlertDispatchService(publisher, broken).dispatch(List.of(alert));

        assertThat(publisher.allAlerts()).containsExactly(alert);
    }

    private static AlertToSend alert(String scenarioName, Symbol symbol, Timeframe timeframe) {
        return new AlertToSend(symbol, Direction.LONG, scenarioName, timeframe,
                new BigDecimal("100"), Optional.empty(), Optional.empty(),
                scenarioName + " " + symbol.code() + " " + timeframe, TestFixtures.FIXED_NOW);
    }
}

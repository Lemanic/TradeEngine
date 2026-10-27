package pl.tradeengine.application.service.fill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.tradeengine.FakeNotificationPublisher;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.adapter.outbound.hyperliquid.HyperliquidException;
import pl.tradeengine.domain.model.fill.TradeFill;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reguly, bez ktorych ten mechanizm byl by szkodliwy: pierwszy przebieg milczy, drugi nie
 * powtarza, a zaleglosci nie zalewaja Telegrama.
 */
class FillAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");
    private static final Duration LOOKBACK = Duration.ofHours(24);
    private static final Duration RETENTION = Duration.ofDays(30);

    private FakeTradeFillSource source;
    private InMemoryTradeFillRepository repository;
    private FakeNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        source = new FakeTradeFillSource();
        repository = new InMemoryTradeFillRepository();
        publisher = new FakeNotificationPublisher();
    }

    private FillAlertService service(int maxMessagesPerRun) {
        return new FillAlertService(source, repository, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), maxMessagesPerRun, LOOKBACK, RETENTION);
    }

    @Test
    void firstRunRemembersExistingFillsWithoutAlertingAboutThem() {
        source.returns(TestFixtures.tradeFill(1, NOW.minus(Duration.ofHours(3))),
                TestFixtures.tradeFill(2, NOW.minus(Duration.ofMinutes(10))));

        service(10).pollAndNotify();

        // Jedna wiadomosc, i to potwierdzajaca — nie dwa alerty o historycznych filach.
        assertThat(publisher.sent()).hasSize(1);
        assertThat(publisher.sent().getFirst())
                .contains("Alerty o realizacji zleceń włączone")
                .contains("Zapamiętano 2");
        assertThat(repository.seenTids()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(source.askedSince()).containsExactly(NOW.minus(LOOKBACK));
    }

    @Test
    void alertsAboutAFillOnceAndNeverAgain() {
        TradeFill known = TestFixtures.tradeFill(1, NOW.minus(Duration.ofHours(2)));
        repository.seed(known);
        TradeFill fresh = TestFixtures.tradeFill(2, NOW.minus(Duration.ofMinutes(1)));
        source.returns(known, fresh);

        service(10).pollAndNotify();

        assertThat(publisher.sent()).hasSize(1);
        assertThat(publisher.sent().getFirst()).contains("HYPERLIQUID").contains("BTC");

        // Granica startTime w API jest domknieta, wiec API oddaje te same fille raz jeszcze.
        publisher.clear();
        service(10).pollAndNotify();

        assertThat(publisher.sent()).isEmpty();
        assertThat(source.askedSince().getLast()).isEqualTo(fresh.time());
    }

    @Test
    void usesTheNewestKnownFillAsTheCursorNotTheLastStoredOne() {
        repository.seed(TestFixtures.tradeFill(1, NOW.minus(Duration.ofHours(5))));
        repository.seed(TestFixtures.tradeFill(2, NOW.minus(Duration.ofHours(1))));
        repository.seed(TestFixtures.tradeFill(3, NOW.minus(Duration.ofHours(3))));

        service(10).pollAndNotify();

        assertThat(source.askedSince()).containsExactly(NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void capsMessagesPerRunAndSaysHowManyWereSkipped() {
        List<TradeFill> backlog = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            backlog.add(TestFixtures.tradeFill(i, NOW.minus(Duration.ofMinutes(60 - i))));
        }
        repository.seed(TestFixtures.tradeFill(100, NOW.minus(Duration.ofHours(6))));
        source.returns(backlog);

        service(3).pollAndNotify();

        assertThat(publisher.sent()).hasSize(4);
        assertThat(publisher.sent().getLast())
                .contains("+9 kolejnych filli pominięto")
                .contains("limit 3");
        // Pominiete tez sa zapamietane — inaczej wrocilyby w nastepnym przebiegu.
        assertThat(repository.seenTids()).hasSize(13);
    }

    @Test
    void sendsTheOldestFillsFirst() {
        repository.seed(TestFixtures.tradeFill(100, NOW.minus(Duration.ofHours(6))));
        source.returns(
                TestFixtures.tradeFill(1, NOW.minus(Duration.ofMinutes(30))),
                TestFixtures.tradeFill(2, NOW.minus(Duration.ofMinutes(20))),
                TestFixtures.tradeFill(3, NOW.minus(Duration.ofMinutes(10))));

        service(2).pollAndNotify();

        assertThat(publisher.sent()).hasSize(3);
        assertThat(publisher.sent().get(0)).contains("14:30 UTC");
        assertThat(publisher.sent().get(1)).contains("14:40 UTC");
    }

    @Test
    void aFailingSourceStoresNothingAndAlertsNobody() {
        repository.seed(TestFixtures.tradeFill(1, NOW.minus(Duration.ofHours(2))));
        source.fails(new HyperliquidException("Hyperliquid is unreachable for userFillsByTime"));

        // Wyjatek leci w gore do schedulera, ktory go loguje — nastepny przebieg probuje znowu.
        assertThatThrownBy(() -> service(10).pollAndNotify())
                .isInstanceOf(HyperliquidException.class);

        assertThat(publisher.sent()).isEmpty();
        assertThat(repository.seenTids()).containsExactly(1L);
    }

    @Test
    void forgetsRowsOlderThanTheRetentionWindow() {
        repository.seed(TestFixtures.tradeFill(1, NOW.minus(Duration.ofDays(31))));
        repository.seed(TestFixtures.tradeFill(2, NOW.minus(Duration.ofDays(29))));

        service(10).pollAndNotify();

        assertThat(repository.seenTids()).containsExactly(2L);
    }

    @Test
    void neverPrunesTheTableDownToEmpty() {
        // Po 30 dniach bez transakcji wszystko jest starsze niz okno retencji. Gdyby czyszczenie
        // zabralo ostatni wiersz, nastepny przebieg byl by "pierwszym" — a ten milczy, wiec fill,
        // ktory trafilby w to okno, nie dostalby alertu.
        repository.seed(TestFixtures.tradeFill(1, NOW.minus(Duration.ofDays(40))));

        service(10).pollAndNotify();

        assertThat(repository.seenTids()).containsExactly(1L);
        assertThat(publisher.sent()).isEmpty();
    }

    @Test
    void aQuietRunSendsNothing() {
        repository.seed(TestFixtures.tradeFill(1, NOW.minus(Duration.ofHours(2))));

        service(10).pollAndNotify();

        assertThat(publisher.sent()).isEmpty();
    }
}

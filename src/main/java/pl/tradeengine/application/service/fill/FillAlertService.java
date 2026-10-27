package pl.tradeengine.application.service.fill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.tradeengine.domain.model.fill.TradeFill;
import pl.tradeengine.domain.port.NotificationPublisher;
import pl.tradeengine.domain.port.fill.TradeFillRepository;
import pl.tradeengine.domain.port.fill.TradeFillSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Jeden przebieg: odczytaj fille od ostatniego znanego, powiadom o nowych, zapamiętaj je.
 *
 * <p>Trzy reguły, bez których ten mechanizm byłby szkodliwy:
 *
 * <ul>
 *   <li><b>Dedup po {@code tid}, nie po czasie.</b> Granica {@code startTime} w API jest
 *       domknięta, a kilka filli może mieć ten sam znacznik w milisekundach, więc ostatni
 *       obsłużony fill wraca w każdym przebiegu. Odsiewa go klucz główny w bazie.</li>
 *   <li><b>Pierwszy przebieg milczy.</b> Pusta tabela nie znaczy „nic się nie wydarzyło”, tylko
 *       „jeszcze nie wiem” — API pamięta tysiące filli i wysłanie ich wszystkich byłoby
 *       zasypaniem Telegrama. Bootstrap zapisuje je jako obsłużone i wysyła jedno
 *       potwierdzenie.</li>
 *   <li><b>Limit wiadomości na przebieg.</b> Po dłuższym przestoju zaległości mogą być liczone w
 *       dziesiątkach. Wysyłamy najstarsze, resztę zapisujemy jako obsłużoną i doklejamy jedną
 *       linię podsumowania — lepiej stracić szczegół niż zalać kanał.</li>
 * </ul>
 *
 * <p>Fill jest zapisywany jako obsłużony także wtedy, gdy wiadomość o nim nie poszła z powodu
 * limitu. Dlatego wysyłka nie może wyrzucać wyjątków — kontrakt {@link NotificationPublisher}
 * tego pilnuje, inaczej jeden błąd sieci powtarzałby alert w nieskończoność.
 */
public class FillAlertService {

    private static final Logger log = LoggerFactory.getLogger(FillAlertService.class);

    private final TradeFillSource source;
    private final TradeFillRepository repository;
    private final NotificationPublisher publisher;
    private final Clock clock;
    private final int maxMessagesPerRun;
    private final Duration bootstrapLookback;
    private final Duration retention;

    public FillAlertService(TradeFillSource source,
                            TradeFillRepository repository,
                            NotificationPublisher publisher,
                            Clock clock,
                            int maxMessagesPerRun,
                            Duration bootstrapLookback,
                            Duration retention) {
        this.source = source;
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.maxMessagesPerRun = maxMessagesPerRun;
        this.bootstrapLookback = bootstrapLookback;
        this.retention = retention;
    }

    public void pollAndNotify() {
        Instant now = clock.instant();
        Optional<Instant> lastKnown = repository.lastFillTime();
        Instant since = lastKnown.orElse(now.minus(bootstrapLookback));

        List<TradeFill> fresh = source.fillsSince(since).stream()
                .filter(fill -> repository.isNew(fill.tid()))
                .toList();

        if (lastKnown.isEmpty()) {
            bootstrap(fresh, now);
        } else {
            notifyAbout(fresh, now);
        }
        prune(now);
    }

    private void bootstrap(List<TradeFill> fills, Instant now) {
        fills.forEach(fill -> repository.markSeen(fill, now));
        log.info("Pierwszy przebieg alertow o filach: zapamietano {} fill(ow) bez powiadamiania",
                fills.size());
        publisher.send(FillMessageFormatter.bootstrap(fills.size(), bootstrapLookback));
    }

    private void notifyAbout(List<TradeFill> fills, Instant now) {
        if (fills.isEmpty()) {
            log.debug("Brak nowych filli");
            return;
        }

        int sent = 0;
        for (TradeFill fill : fills) {
            if (sent < maxMessagesPerRun) {
                publisher.send(FillMessageFormatter.format(fill));
                sent++;
            }
            repository.markSeen(fill, now);
        }

        int skipped = fills.size() - sent;
        if (skipped > 0) {
            log.warn("Zalegosci: {} fill(ow) ponad limit {} wiadomosci na przebieg",
                    skipped, maxMessagesPerRun);
            publisher.send(FillMessageFormatter.skipped(skipped, maxMessagesPerRun));
        }
        log.info("Powiadomiono o {} fill(ach), pominieto {}", sent, skipped);
    }

    private void prune(Instant now) {
        Instant cutoff = now.minus(retention);

        // Tabeli nie wolno wyczyscic do zera: pusta znaczy "pierwszy przebieg", a ten milczy.
        // Po 30 dniach bez transakcji wszystkie wiersze sa starsze niz okno, wiec bez tego
        // warunku nastepny przebieg zaczalby od bootstrapu i zjadlby alert o fillu, ktory trafil
        // akurat miedzy tymi dwoma przebiegami.
        if (repository.lastFillTime().filter(last -> !last.isBefore(cutoff)).isEmpty()) {
            return;
        }

        int deleted = repository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.debug("Usunieto {} wiersz(y) starszych niz {}", deleted, retention);
        }
    }
}

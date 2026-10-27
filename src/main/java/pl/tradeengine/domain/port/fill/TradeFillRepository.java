package pl.tradeengine.domain.port.fill;

import pl.tradeengine.domain.model.fill.TradeFill;

import java.time.Instant;
import java.util.Optional;

/**
 * Pamięć o filach, o których już powiadomiono. Robi dwie rzeczy naraz i obie na jednej tabeli:
 * daje kursor (najpóźniejszy znany czas filla) i odsiewa duplikaty (klucz główny na {@code tid}).
 */
public interface TradeFillRepository {

    /**
     * Czas najpóźniejszego zapamiętanego filla, albo puste gdy nic nie zapisano.
     *
     * <p>Puste znaczy „pierwsze uruchomienie” i uruchamia bootstrap — nie wolno tego pomylić z
     * „nic się nie wydarzyło”, bo API pamięta tysiące historycznych filli.
     */
    Optional<Instant> lastFillTime();

    /** Czy o tym fillu jeszcze nie powiadomiono. */
    boolean isNew(long tid);

    /** Zapisuje fill jako obsłużony — także gdy wiadomość o nim świadomie nie poszła. */
    void markSeen(TradeFill fill, Instant notifiedAt);

    /** Czyści stare wiersze; zwraca liczbę usuniętych. */
    int deleteOlderThan(Instant cutoff);
}

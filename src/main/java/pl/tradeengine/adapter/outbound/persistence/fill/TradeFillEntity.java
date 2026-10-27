package pl.tradeengine.adapter.outbound.persistence.fill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Fill, o którym już powiadomiono. {@code tid} nadany przez giełdę jest kluczem głównym — to on
 * sprawia, że powtórny odczyt tego samego filla nie wywoła drugiej wiadomości, bez potrzeby
 * pamiętania osobnego kursora.
 *
 * <p>Wiersz nie trzyma ani ceny, ani ilości: to nie jest historia transakcji, tylko pamięć
 * „to już obsłużone”. Wartości są w wiadomości, która poszła, i na giełdzie.
 */
@Entity
@Getter
@Setter
@Table(name = "hyperliquid_fill")
public class TradeFillEntity {

    @Id
    @Column(name = "tid", nullable = false)
    private Long tid;

    @Column(name = "fill_time", nullable = false)
    private OffsetDateTime fillTime;

    @Column(name = "coin", nullable = false, length = 64)
    private String coin;

    @Column(name = "notified_at", nullable = false)
    private OffsetDateTime notifiedAt;
}

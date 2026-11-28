package pl.tradeengine.adapter.outbound.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Sprawdza to, czego fake nie sprawdzi: ze regula "wszystkie interwaly" (timeframe = NULL) nie
 * dubluje sie przy drugim zapisie — wlasnie dlatego kluczem jest zlozony string, a nie UNIQUE na
 * trzech kolumnach — i ze usuniecie trafia tylko w jedna regule.
 */
@DataJpaTest
@Import(AlertMutePersistenceAdapter.class)
@ActiveProfiles("test")
class AlertMutePersistenceAdapterTest {

    private static final Instant FIRST = Instant.parse("2026-11-28T09:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-28T10:00:00Z");
    private static final Symbol TAO = new Symbol("TAOUSDT");

    @Autowired
    private AlertMutePersistenceAdapter adapter;

    @Test
    void storesAndReadsBackARuleWithoutTimeframe() {
        adapter.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, FIRST, null));

        assertThat(adapter.findAll()).containsExactly(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, FIRST, null));
    }

    @Test
    void keepsWhoMuted() {
        adapter.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, Timeframe.H1, FIRST, "owner@example.com"));

        assertThat(adapter.findAll()).singleElement()
                .extracting(AlertMute::mutedBy).isEqualTo("owner@example.com");
    }

    @Test
    void theAllTimeframesRuleIsReplacedRatherThanDuplicated() {
        adapter.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, FIRST, null));
        adapter.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, SECOND, null));

        assertThat(adapter.findAll()).singleElement()
                .extracting(AlertMute::mutedAt).isEqualTo(SECOND);
    }

    @Test
    void aTimeframeRuleAndTheAllTimeframesRuleAreTwoRules() {
        adapter.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, null, FIRST, null));
        adapter.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1, FIRST, null));

        assertThat(adapter.findAll()).hasSize(2);
    }

    @Test
    void deleteRemovesExactlyOneRule() {
        adapter.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, null, FIRST, null));
        adapter.save(new AlertMute("DOUBLE_DIVERGENCE_STRATEGY", TAO, Timeframe.H1, FIRST, null));
        adapter.save(new AlertMute("GRINDER_SWING_D1_H1", TAO, null, FIRST, null));

        adapter.delete("DOUBLE_DIVERGENCE_STRATEGY", TAO, null);

        assertThat(adapter.findAll())
                .extracting(AlertMute::strategy, AlertMute::timeframe)
                .containsExactlyInAnyOrder(
                        tuple("DOUBLE_DIVERGENCE_STRATEGY", Timeframe.H1),
                        tuple("GRINDER_SWING_D1_H1", null));
    }

    @Test
    void deletingAMissingRuleIsNotAnError() {
        adapter.delete("GRINDER_SWING_D1_H1", TAO, Timeframe.H4);

        assertThat(adapter.findAll()).isEmpty();
    }

    @Test
    void aTickerWithPunctuationSurvivesTheRoundTrip() {
        Symbol nq = new Symbol("CME_MINI:NQ1!");
        adapter.save(new AlertMute("GRINDER_SWING_D1_H1", nq, Timeframe.H1, FIRST, null));

        assertThat(adapter.findAll()).singleElement().extracting(AlertMute::symbol).isEqualTo(nq);
    }
}

package pl.tradeengine.config;

import org.junit.jupiter.api.Test;
import pl.tradeengine.RecordingScenario;
import pl.tradeengine.TestFixtures;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.scenario.Scenario;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wlaczanie strategii jest sterowane danymi: bean deklaruje name(), a YAML decyduje o enabled
 * i timeframes. Te testy pilnuja trybu awarii opisanego w CLAUDE.md — "zmiana name() bez
 * aktualizacji YAML cicho wylacza strategie".
 */
class ScenarioRegistryTest {

    private static final RecordingScenario LTF = RecordingScenario.silent("FVG_LTF_CONFIRMATION");
    private static final RecordingScenario GRINDER = RecordingScenario.silent("GRINDER_SWING_D1_H1");

    @Test
    void shouldExcludeDisabledStrategies_evenWhenTheBeanExists() {
        ScenarioRegistry registry = new ScenarioRegistry(
                List.of(LTF, GRINDER),
                TestFixtures.strategies(
                        TestFixtures.strategy("FVG_LTF_CONFIRMATION", true, Timeframe.H1),
                        TestFixtures.strategy("GRINDER_SWING_D1_H1", false, Timeframe.H1)));

        assertThat(registry.getAllEnabledScenarios()).containsExactly(LTF);
    }

    @Test
    void shouldFilterByTimeframe_inGetScenariosFor() {
        ScenarioRegistry registry = new ScenarioRegistry(
                List.of(LTF, GRINDER),
                TestFixtures.strategies(
                        TestFixtures.strategy("FVG_LTF_CONFIRMATION", true, Timeframe.M5, Timeframe.H1),
                        TestFixtures.strategy("GRINDER_SWING_D1_H1", true, Timeframe.H4)));

        assertThat(registry.getScenariosFor(Timeframe.M5)).containsExactly(LTF);
        assertThat(registry.getScenariosFor(Timeframe.H4)).containsExactly(GRINDER);
        assertThat(registry.getScenariosFor(Timeframe.D1)).isEmpty();
    }

    @Test
    void shouldIgnoreTimeframes_inGetAllEnabledScenarios() {
        // Asymetria warta utrwalenia: ScenarioEngine uzywa getAllEnabledScenarios dla wszystkich
        // zdarzen poza DivergenceDetectedEvent, czyli timeframe z YAML tam nie filtruje niczego.
        ScenarioRegistry registry = new ScenarioRegistry(
                List.of(LTF),
                TestFixtures.strategies(TestFixtures.strategy("FVG_LTF_CONFIRMATION", true, Timeframe.W1)));

        assertThat(registry.getScenariosFor(Timeframe.M1)).isEmpty();
        assertThat(registry.getAllEnabledScenarios()).containsExactly(LTF);
    }

    @Test
    void shouldSilentlySkipYamlEntry_whenNoBeanHasThatName() {
        // Dokladnie ten tryb awarii opisuje CLAUDE.md: literowka w name() albo w YAML nie wywala
        // startu, tylko cicho wylacza strategie. Objects::nonNull zjada brak.
        ScenarioRegistry registry = new ScenarioRegistry(
                List.of(LTF),
                TestFixtures.strategies(
                        TestFixtures.strategy("FVG_LTF_CONFIRMATION", true, Timeframe.H1),
                        TestFixtures.strategy("STRATEGIA_KTOREJ_NIE_MA", true, Timeframe.H1)));

        assertThat(registry.getScenariosFor(Timeframe.H1)).containsExactly(LTF);
        assertThat(registry.getAllEnabledScenarios()).containsExactly(LTF);
    }

    @Test
    void shouldSkipBeanWithNoYamlEntry_becauseEnablementComesFromConfigOnly() {
        // Odwrotna strona tej samej monety: sama obecnosc beana na classpathie nic nie wlacza.
        ScenarioRegistry registry = new ScenarioRegistry(
                List.of(LTF, GRINDER),
                TestFixtures.strategies(TestFixtures.strategy("FVG_LTF_CONFIRMATION", true, Timeframe.H1)));

        assertThat(registry.getAllEnabledScenarios()).containsExactly(LTF).doesNotContain(GRINDER);
    }

    @Test
    void shouldThrowOnConstruction_whenTwoScenariosShareTheSameName() {
        // Collectors.toMap bez funkcji scalajacej. Lepiej, zeby to padlo na starcie kontekstu
        // niz zeby jeden scenariusz po cichu przeslonil drugi.
        List<Scenario> duplicates = List.of(
                RecordingScenario.silent("FVG_LTF_CONFIRMATION"),
                RecordingScenario.silent("FVG_LTF_CONFIRMATION"));

        assertThatThrownBy(() -> new ScenarioRegistry(duplicates, TestFixtures.strategies()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate key");
    }

    @Test
    void shouldThrowNullPointer_whenStrategyListIsMissingFromConfiguration() {
        // Realna pulapka: profil bez klucza app.strategies. Dlatego application-test.yml ma teraz
        // ten blok — bez niego kazdy @SpringBootTest na profilu test padal tutaj.
        StrategyProperties noStrategies = new StrategyProperties();
        ScenarioRegistry registry = new ScenarioRegistry(List.of(LTF), noStrategies);

        assertThatThrownBy(registry::getAllEnabledScenarios).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.getScenariosFor(Timeframe.H1)).isInstanceOf(NullPointerException.class);
    }
}

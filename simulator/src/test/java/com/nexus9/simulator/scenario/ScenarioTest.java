package com.nexus9.simulator.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ScenarioTest {

    @Test
    void findsBuiltInScenariosByName() {
        assertThat(Scenario.byName("solar-degradation")).isEqualTo(Scenario.SOLAR_DEGRADATION);
        assertThatThrownBy(() -> Scenario.byName("x")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void returnsFaultsScheduledForATick() {
        Scenario scenario = new Scenario("test", List.of(
                new PanelFault(10, "A", 0.5),
                new PanelFault(5, "B", 0.0),
                new PanelFault(10, "B", 0.2)));

        assertThat(scenario.faultsAt(10)).extracting(f -> ((PanelFault) f).panelId()).containsExactly("A", "B");
        assertThat(scenario.faultsAt(7)).isEmpty();
        assertThat(scenario.faults().getFirst().tick()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidFaults() {
        assertThatThrownBy(() -> new PanelFault(-1, "A", 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PanelFault(0, " ", 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PanelFault(0, "A", 1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PanelFault(0, "A", Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PanelFault(0, "A", Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

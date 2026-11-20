package com.nexus9.backend.incident;

import static com.nexus9.backend.domain.StationModule.COMMS;
import static com.nexus9.backend.domain.StationModule.OXYGEN;
import static com.nexus9.backend.domain.StationModule.POWER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DependencyGraphTest {

    @Test
    void powerAffectsOxygenAndComms() {
        assertThat(DependencyGraph.STATION.affectedBy(POWER)).containsExactly(OXYGEN, COMMS);
        assertThat(DependencyGraph.STATION.affectedBy(OXYGEN)).isEmpty();
    }

    @Test
    void followsIndirectDependenciesOnce() {
        // COMMS -> OXYGEN -> POWER, and COMMS -> POWER directly.
        DependencyGraph graph = new DependencyGraph(Map.of(
                OXYGEN, List.of(POWER),
                COMMS, List.of(OXYGEN, POWER)));

        assertThat(graph.affectedBy(POWER)).containsExactly(OXYGEN, COMMS);
    }
}

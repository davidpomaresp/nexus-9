package com.nexus9.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.backend.incident.Incident;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.backend.simulation.StationRegistry;
import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.Station;
import com.nexus9.simulator.scenario.Scenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Failure chain with a backend restart and duplicated events in the middle. The result must be
 * identical to an uninterrupted run.
 */
class EndToEndScenarioTest extends PostgresTestBase {

    @Autowired
    SimulationService simulations;

    @Autowired
    IncidentService incidents;

    @Autowired
    TelemetryRepository telemetry;

    @Autowired
    StationRegistry registry;

    @Test
    void restartsAndDuplicatesDoNotChangeTheOutcome() {
        Simulation reference = simulations.create(2026, "solar-degradation");
        simulations.advance(reference.id(), 600);

        Simulation disturbed = simulations.create(2026, "solar-degradation");
        simulations.advance(disturbed.id(), 300);
        registry.clear();
        replayAlreadyStoredEvents(disturbed.id(), 300);
        simulations.advance(disturbed.id(), 150);
        registry.clear();
        simulations.advance(disturbed.id(), 150);

        assertThat(telemetry.count(disturbed.id())).isEqualTo(600 * 3);
        assertThat(summary(disturbed.id())).isEqualTo(summary(reference.id())).isNotEmpty();
    }

    private void replayAlreadyStoredEvents(UUID simulationId, int ticks) {
        Station station = new Station(2026, Scenario.SOLAR_DEGRADATION);
        for (int i = 0; i < ticks; i++) {
            assertThat(telemetry.append(simulationId, station.advance())).isZero();
        }
    }

    private List<String> summary(UUID simulationId) {
        return incidents.find(simulationId, Optional.empty()).stream()
                .map(i -> i.ruleCode() + "@" + i.openedAtTick() + "-" + i.resolvedAtTick() + ":" + i.status())
                .toList();
    }
}

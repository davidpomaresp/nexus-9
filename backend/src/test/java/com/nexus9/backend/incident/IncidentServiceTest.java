package com.nexus9.backend.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.backend.simulation.StationModule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentServiceTest extends PostgresTestBase {

    @Autowired
    SimulationService simulations;

    @Autowired
    IncidentService incidents;

    @Autowired
    IncidentRepository repository;

    @Test
    void nominalScenarioRaisesNoIncidents() {
        Simulation sim = simulations.create(42, "nominal");
        simulations.advance(sim.id(), 1000);

        assertThat(incidents.find(sim.id(), Optional.empty())).isEmpty();
    }

    @Test
    void solarDegradationRaisesIncidentsInCascade() {
        Simulation sim = simulations.create(42, "solar-degradation");
        simulations.advance(sim.id(), 600);

        Map<String, Incident> byRule = incidents.find(sim.id(), Optional.of(IncidentStatus.OPEN)).stream()
                .collect(Collectors.toMap(Incident::ruleCode, Function.identity()));

        assertThat(byRule).containsOnlyKeys(
                "GENERATION_DEFICIT", "BATTERY_LOW", "BATTERY_CRITICAL", "COMMS_DEGRADED", "OXYGEN_LOW");
        assertThat(byRule.get("GENERATION_DEFICIT").openedAtTick()).isEqualTo(120);
        assertThat(byRule.get("GENERATION_DEFICIT").openedAtTick())
                .isLessThan(byRule.get("COMMS_DEGRADED").openedAtTick());
        assertThat(byRule.get("COMMS_DEGRADED").openedAtTick())
                .isLessThan(byRule.get("BATTERY_LOW").openedAtTick());
        assertThat(byRule.get("BATTERY_CRITICAL").openedAtTick())
                .isLessThan(byRule.get("OXYGEN_LOW").openedAtTick());
        assertThat(byRule.get("BATTERY_LOW").affectedModules())
                .containsExactly(StationModule.OXYGEN, StationModule.COMMS);
        assertThat(byRule.get("OXYGEN_LOW").priority()).isEqualTo(Priority.CRITICAL);
    }

    @Test
    void databaseRejectsASecondOpenIncidentForTheSameRule() {
        Simulation sim = simulations.create(1, "nominal");

        boolean first = repository.insertOpen(incident(sim.id(), 10));
        boolean second = repository.insertOpen(incident(sim.id(), 11));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(incidents.find(sim.id(), Optional.empty()).getFirst().resolvedAtTick()).isNull();
        assertThat(incidents.find(sim.id(), Optional.empty())).hasSize(1);
    }

    @Test
    void aNewIncidentCanOpenAfterThePreviousOneIsResolved() {
        Simulation sim = simulations.create(1, "nominal");
        Incident first = incident(sim.id(), 10);
        repository.insertOpen(first);
        repository.resolve(first.id(), 20);

        boolean reopened = repository.insertOpen(incident(sim.id(), 30));

        assertThat(reopened).isTrue();
        assertThat(incidents.find(sim.id(), Optional.of(IncidentStatus.RESOLVED)))
                .extracting(Incident::resolvedAtTick).containsExactly(20L);
    }

    private static Incident incident(UUID simulationId, long tick) {
        return new Incident(UUID.randomUUID(), simulationId, StationModule.POWER, "BATTERY_LOW",
                Priority.HIGH, IncidentStatus.OPEN, tick, null, List.of(StationModule.OXYGEN));
    }
}

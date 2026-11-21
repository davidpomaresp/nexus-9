package com.nexus9.backend.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.domain.StationModule;
import com.nexus9.backend.simulation.Simulation;
import com.nexus9.backend.simulation.SimulationService;
import com.nexus9.simulator.StationSnapshot;
import com.nexus9.simulator.comms.CommsMode;
import com.nexus9.simulator.comms.CommsReading;
import com.nexus9.simulator.oxygen.OxygenReading;
import com.nexus9.simulator.power.PowerReading;
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
    void oxygenLeakOpensAndResolvesOxygenLow() {
        Simulation sim = simulations.create(42, "oxygen-leak");
        simulations.advance(sim.id(), 600);

        List<Incident> all = incidents.find(sim.id(), Optional.empty());

        assertThat(all).extracting(Incident::ruleCode).containsExactly("OXYGEN_LOW");
        Incident leak = all.getFirst();
        assertThat(leak.openedAtTick()).isBetween(150L, 200L);
        assertThat(leak.resolvedAtTick()).isGreaterThan(300L);
        assertThat(leak.status()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void solarStormIncidentsAreResolvedAfterTheStorm() {
        Simulation sim = simulations.create(42, "solar-storm");
        simulations.advance(sim.id(), 900);

        List<Incident> all = incidents.find(sim.id(), Optional.empty());

        assertThat(all).extracting(Incident::ruleCode)
                .contains("GENERATION_DEFICIT", "COMMS_DEGRADED", "BATTERY_LOW")
                .doesNotContain("BATTERY_CRITICAL", "OXYGEN_LOW");
        assertThat(all).filteredOn(i -> i.ruleCode().equals("GENERATION_DEFICIT"))
                .singleElement()
                .satisfies(i -> {
                    // The storm starts at 180 in eclipse; the deficit shows once the station is back in the sun.
                    assertThat(i.openedAtTick()).isEqualTo(184);
                    assertThat(i.resolvedAtTick()).isGreaterThanOrEqualTo(420L);
                });
        assertThat(incidents.find(sim.id(), Optional.of(IncidentStatus.OPEN))).isEmpty();
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

    @Test
    void batteryIncidentsFollowTheirHysteresisCycle() {
        UUID id = simulations.create(1, "nominal").id();

        incidents.evaluate(id, battery(1, 19));
        assertThat(state(id)).containsExactly("BATTERY_LOW@1-null:OPEN");

        incidents.evaluate(id, battery(2, 22));
        assertThat(state(id)).containsExactly("BATTERY_LOW@1-null:OPEN");

        incidents.evaluate(id, battery(3, 26));
        assertThat(state(id)).containsExactly("BATTERY_LOW@1-3:RESOLVED");

        incidents.evaluate(id, battery(4, 4));
        assertThat(state(id)).containsExactly(
                "BATTERY_LOW@1-3:RESOLVED", "BATTERY_CRITICAL@4-null:OPEN", "BATTERY_LOW@4-null:OPEN");

        incidents.evaluate(id, battery(5, 10));
        assertThat(incidents.countOpen(id)).isEqualTo(2);

        incidents.evaluate(id, battery(6, 10.5));
        assertThat(state(id)).containsExactly(
                "BATTERY_LOW@1-3:RESOLVED", "BATTERY_CRITICAL@4-6:RESOLVED", "BATTERY_LOW@4-null:OPEN");
    }

    private List<String> state(UUID simulationId) {
        return incidents.find(simulationId, Optional.empty()).stream()
                .map(i -> i.ruleCode() + "@" + i.openedAtTick() + "-" + i.resolvedAtTick() + ":" + i.status())
                .toList();
    }

    private static StationSnapshot battery(long tick, double percent) {
        return new StationSnapshot(tick,
                new PowerReading(tick, false, 0, 6.5, 6.5, percent / 5, percent),
                new OxygenReading(tick, true, 100),
                new CommsReading(tick, CommsMode.FULL));
    }

    private static Incident incident(UUID simulationId, long tick) {
        return new Incident(UUID.randomUUID(), simulationId, StationModule.POWER, "BATTERY_LOW",
                Priority.HIGH, IncidentStatus.OPEN, tick, null, List.of(StationModule.OXYGEN));
    }
}

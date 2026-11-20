package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.domain.StationModule;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.Station;
import com.nexus9.simulator.StationSnapshot;
import com.nexus9.simulator.scenario.Scenario;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SimulationServiceTest extends PostgresTestBase {

    @Autowired
    SimulationService service;

    @Autowired
    TelemetryRepository telemetry;

    @Autowired
    StationRegistry registry;

    @Autowired
    IncidentService incidents;

    @Autowired
    JdbcClient jdbc;

    @Test
    void advanceStoresOneEventPerModuleAndTick() {
        Simulation sim = service.create(42, "nominal");

        Simulation after = service.advance(sim.id(), 10).simulation();

        assertThat(after.currentTick()).isEqualTo(10);
        assertThat(service.get(sim.id()).currentTick()).isEqualTo(10);
        assertThat(telemetry.count(sim.id())).isEqualTo(30);
    }

    @Test
    void appendingTheSameSnapshotTwiceIsIgnored() {
        Simulation sim = service.create(42, "nominal");
        StationSnapshot snapshot = new Station(42, Scenario.NOMINAL).advance();

        int first = telemetry.append(sim.id(), snapshot);
        int second = telemetry.append(sim.id(), snapshot);

        assertThat(first).isEqualTo(3);
        assertThat(second).isZero();
        assertThat(telemetry.count(sim.id())).isEqualTo(3);
    }

    @Test
    void historyIsTheSameAfterARestart() {
        Simulation interrupted = service.create(7, "solar-degradation");
        service.advance(interrupted.id(), 250);
        registry.clear();
        service.advance(interrupted.id(), 250);

        Simulation continuous = service.create(7, "solar-degradation");
        service.advance(continuous.id(), 500);

        for (StationModule module : StationModule.values()) {
            assertThat(values(interrupted.id(), module)).isEqualTo(values(continuous.id(), module));
        }
    }

    @Test
    void concurrentAdvancesGiveTheSameHistoryAsASerialRun() throws Exception {
        Simulation reference = service.create(7, "solar-degradation");
        service.advance(reference.id(), 400);

        Simulation sim = service.create(7, "solar-degradation");
        int threads = 4;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return service.advance(sim.id(), 100);
                }));
            }
            for (Future<?> run : runs) {
                run.get(2, TimeUnit.MINUTES);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(service.get(sim.id()).currentTick()).isEqualTo(400);
        for (StationModule module : StationModule.values()) {
            assertThat(values(sim.id(), module)).isEqualTo(values(reference.id(), module)).hasSize(400);
        }
        assertThat(incidentsOf(sim.id())).isEqualTo(incidentsOf(reference.id())).isNotEmpty();
    }

    @Test
    void advancingToATickIsIdempotent() {
        Simulation sim = service.create(42, "nominal");

        assertThat(service.advanceTo(sim.id(), 30).simulation().currentTick()).isEqualTo(30);
        assertThat(service.advanceTo(sim.id(), 30).simulation().currentTick()).isEqualTo(30);
        assertThat(service.advanceTo(sim.id(), 10).simulation().currentTick()).isEqualTo(30);
        assertThat(telemetry.count(sim.id())).isEqualTo(90);
    }

    @Test
    void refusesToContinueASimulationFromAnotherModelVersion() {
        Simulation sim = service.create(42, "nominal");
        service.advance(sim.id(), 5);
        jdbc.sql("UPDATE simulation SET model_version = ? WHERE id = ?")
                .params(Station.MODEL_VERSION - 1, sim.id())
                .update();

        assertThatThrownBy(() -> service.advance(sim.id(), 1)).isInstanceOf(ModelVersionMismatchException.class);
        assertThat(service.get(sim.id()).currentTick()).isEqualTo(5);
    }

    @Test
    void rejectsUnknownScenariosAndSimulations() {
        assertThatThrownBy(() -> service.create(1, "nope")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.advance(UUID.randomUUID(), 1))
                .isInstanceOf(SimulationNotFoundException.class);
        Simulation sim = service.create(1, "nominal");
        assertThatThrownBy(() -> service.advance(sim.id(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> incidentsOf(UUID id) {
        return incidents.find(id, Optional.empty()).stream()
                .map(i -> i.ruleCode() + "@" + i.openedAtTick() + "-" + i.resolvedAtTick() + ":" + i.status())
                .toList();
    }

    private List<Object> values(UUID id, StationModule module) {
        return telemetry.find(id, module, 0, Long.MAX_VALUE).stream()
                .map(r -> (Object) List.of(r.tick(), r.values()))
                .toList();
    }
}

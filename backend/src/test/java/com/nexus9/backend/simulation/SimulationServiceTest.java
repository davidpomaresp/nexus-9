package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.telemetry.TelemetryRecord;
import com.nexus9.backend.telemetry.TelemetryRepository;
import com.nexus9.simulator.Station;
import com.nexus9.simulator.StationSnapshot;
import com.nexus9.simulator.scenario.Scenario;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SimulationServiceTest extends PostgresTestBase {

    @Autowired
    SimulationService service;

    @Autowired
    TelemetryRepository telemetry;

    @Autowired
    StationRegistry registry;

    @Test
    void advanceStoresOneEventPerModuleAndTick() {
        Simulation sim = service.create(42, "nominal");

        Simulation after = service.advance(sim.id(), 10);

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
    void concurrentAdvancesDoNotSkipOrRepeatTicks() {
        Simulation sim = service.create(1, "nominal");

        CompletableFuture<?> a = CompletableFuture.runAsync(() -> service.advance(sim.id(), 50));
        CompletableFuture<?> b = CompletableFuture.runAsync(() -> service.advance(sim.id(), 50));
        CompletableFuture.allOf(a, b).join();

        assertThat(service.get(sim.id()).currentTick()).isEqualTo(100);
        assertThat(telemetry.find(sim.id(), StationModule.POWER, 0, 1000))
                .extracting(TelemetryRecord::tick)
                .containsExactlyElementsOf(java.util.stream.LongStream.range(0, 100).boxed().toList());
    }

    @Test
    void rejectsUnknownScenariosAndSimulations() {
        assertThatThrownBy(() -> service.create(1, "nope")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.advance(UUID.randomUUID(), 1))
                .isInstanceOf(SimulationNotFoundException.class);
        Simulation sim = service.create(1, "nominal");
        assertThatThrownBy(() -> service.advance(sim.id(), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<Object> values(UUID id, StationModule module) {
        return telemetry.find(id, module, 0, Long.MAX_VALUE).stream()
                .map(r -> (Object) List.of(r.tick(), r.values()))
                .toList();
    }
}

package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.simulator.Station;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class SimulationClockTest extends PostgresTestBase {

    @Autowired
    SimulationService service;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SimulationRepository repository;

    @Test
    void runningSimulationsAdvanceUntilPaused() throws Exception {
        UUID id = service.create(42, "nominal").id();

        service.run(id, 10);
        waitUntil(() -> service.get(id).currentTick() >= 30);
        service.pause(id);
        Thread.sleep(500);
        long paused = service.get(id).currentTick();
        Thread.sleep(600);

        assertThat(service.get(id).currentTick()).isEqualTo(paused);
        assertThat(paused % 10).isZero();
        assertThat(service.get(id).running()).isFalse();
    }

    @Test
    void stopsASimulationThatCannotAdvance() throws Exception {
        UUID id = service.create(42, "nominal").id();
        jdbc.sql("UPDATE simulation SET model_version = ? WHERE id = ?")
                .params(Station.MODEL_VERSION - 1, id)
                .update();

        service.run(id, 5);
        waitUntil(() -> !service.get(id).running());

        assertThat(service.get(id).currentTick()).isZero();
    }

    @Test
    void aStepNeverOvertakesAPause() {
        UUID id = service.create(42, "nominal").id();
        service.advance(id, 3);

        service.clockStep(id);

        assertThat(service.get(id).currentTick()).isEqualTo(3);
    }

    @Test
    void ignoresSimulationsDeletedMeanwhile() {
        UUID id = service.create(42, "nominal").id();
        Simulation stale = repository.findById(id).orElseThrow();
        jdbc.sql("DELETE FROM simulation WHERE id = ?").param(id).update();

        new SimulationClock(repository, service).step(stale);

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void rejectsInvalidSpeeds() {
        UUID id = service.create(1, "nominal").id();

        assertThatThrownBy(() -> service.run(id, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.run(id, SimulationService.MAX_SPEED + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met in time");
            }
            Thread.sleep(50);
        }
    }
}

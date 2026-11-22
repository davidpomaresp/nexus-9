package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.telemetry.TelemetryRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** The application's own clock waits an hour between steps here, so tests can mark simulations running. */
@TestPropertySource(properties = "nexus.clock.interval=1h")
class SimulationLimitsTest extends PostgresTestBase {

    @Autowired
    SimulationService service;

    @Autowired
    SimulationRepository repository;

    @Autowired
    TelemetryRepository telemetry;

    @Autowired
    IncidentService incidents;

    @Autowired
    StationRegistry stations;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    JdbcClient jdbc;

    @Test
    void theClockStopsAtTheTickLimit() {
        SimulationService limited = limitedService(12, 0);
        UUID id = limited.create(42, "nominal").id();
        SimulationClock clock = new SimulationClock(repository, limited);

        limited.run(id, 5);
        for (int i = 0; i < 4; i++) {
            clock.tick();
        }

        assertThat(limited.get(id).currentTick()).isEqualTo(12);
        assertThat(limited.get(id).running()).isFalse();
    }

    @Test
    void manualRequestsCannotPassTheTickLimit() {
        SimulationService limited = limitedService(12, 0);
        UUID id = limited.create(42, "nominal").id();
        limited.advanceTo(id, 12);

        assertThatThrownBy(() -> limited.advance(id, 1)).isInstanceOf(DemoLimitException.class);
        assertThatThrownBy(() -> limited.advanceTo(id, 13)).isInstanceOf(DemoLimitException.class);
        assertThatThrownBy(() -> limited.run(id, 5)).isInstanceOf(DemoLimitException.class);
        assertThat(limited.get(id).currentTick()).isEqualTo(12);
    }

    @Test
    void aPauseWinsOverAStepWaitingForTheLock() throws Exception {
        UUID id = service.create(42, "nominal").id();
        service.run(id, 5);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> pauser = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            repository.findByIdForUpdate(id);
            locked.countDown();
            await(release);
            repository.updateClock(id, false, 5);
        }));
        locked.await(5, TimeUnit.SECONDS);
        CompletableFuture<Void> step = CompletableFuture.runAsync(() -> service.clockStep(id));
        Thread.sleep(200);
        release.countDown();
        pauser.get(5, TimeUnit.SECONDS);
        step.get(5, TimeUnit.SECONDS);

        assertThat(service.get(id).currentTick()).isZero();
        assertThat(service.get(id).running()).isFalse();
    }

    @Test
    void capsTheNumberOfSimulations() {
        long existing = jdbc.sql("SELECT count(*) FROM simulation").query(Long.class).single();
        SimulationService limited = limitedService(0, (int) existing + 1);

        limited.create(1, "nominal");

        assertThatThrownBy(() -> limited.create(2, "nominal")).isInstanceOf(DemoLimitException.class);
    }

    private SimulationService limitedService(long maxTick, int maxSimulations) {
        return new SimulationService(repository, telemetry, incidents, stations, tx, events, maxTick,
                maxSimulations);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

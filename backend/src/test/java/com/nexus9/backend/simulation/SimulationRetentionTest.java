package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.telemetry.TelemetryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {"nexus.retention=24h", "nexus.clock.interval=1h"})
class SimulationRetentionTest extends PostgresTestBase {

    @Autowired
    SimulationRetention retention;

    @Autowired
    SimulationService service;

    @Autowired
    TelemetryRepository telemetry;

    @Autowired
    IncidentService incidents;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SimulationRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Test
    void deletesOldSimulationsWithTheirData() {
        UUID old = service.create(42, "solar-degradation").id();
        service.advance(old, 200);
        jdbc.sql("UPDATE simulation SET created_at = now() - interval '2 days' WHERE id = ?").param(old).update();
        UUID recent = service.create(42, "nominal").id();
        service.advance(recent, 5);

        retention.purgeOlderThan(Instant.now().minusSeconds(86_400));

        assertThatThrownBy(() -> service.get(old)).isInstanceOf(SimulationNotFoundException.class);
        assertThat(telemetry.count(old)).isZero();
        assertThat(incidents.find(old, Optional.empty())).isEmpty();
        assertThat(service.get(recent).currentTick()).isEqualTo(5);
    }

    @Test
    void keepsRunningSimulationsAndOnesATickIsHolding() throws Exception {
        UUID running = service.create(42, "nominal").id();
        jdbc.sql("UPDATE simulation SET running = true, created_at = now() - interval '2 days' WHERE id = ?")
                .param(running).update();
        UUID busy = service.create(42, "nominal").id();
        jdbc.sql("UPDATE simulation SET created_at = now() - interval '2 days' WHERE id = ?").param(busy).update();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> tick = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            repository.findByIdForUpdate(busy);
            locked.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        locked.await(5, TimeUnit.SECONDS);

        var deleted = retention.purgeOlderThan(Instant.now().minusSeconds(86_400));
        release.countDown();
        tick.get(5, TimeUnit.SECONDS);

        assertThat(deleted).doesNotContain(running, busy);
        assertThat(repository.findById(busy)).isPresent();
        jdbc.sql("UPDATE simulation SET running = false WHERE id = ?").param(running).update();
    }
}

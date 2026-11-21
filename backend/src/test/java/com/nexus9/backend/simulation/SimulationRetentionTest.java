package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.incident.IncidentService;
import com.nexus9.backend.telemetry.TelemetryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "nexus.retention=24h")
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
}

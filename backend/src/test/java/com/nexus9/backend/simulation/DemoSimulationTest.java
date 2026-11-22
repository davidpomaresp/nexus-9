package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus9.backend.PostgresTestBase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"nexus.demo.scenario=solar-degradation", "nexus.demo.tick=50",
        "nexus.clock.interval=1h"})
class DemoSimulationTest extends PostgresTestBase {

    @Autowired
    DemoSimulation demo;

    @Autowired
    SimulationService service;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    SimulationRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Test
    void keepsOneFeaturedSimulationAdvancedToTheStartingTick() {
        UUID first = demo.id();
        UUID again = demo.id();

        assertThat(again).isEqualTo(first);
        assertThat(service.get(first).currentTick()).isEqualTo(50);
        assertThat(service.get(first).scenario()).isEqualTo("solar-degradation");
    }

    @Test
    void createsANewOneAfterTheOldOneIsDeleted() {
        UUID old = demo.id();
        jdbc.sql("DELETE FROM telemetry_event WHERE simulation_id = ?").param(old).update();
        jdbc.sql("DELETE FROM incident WHERE simulation_id = ?").param(old).update();
        jdbc.sql("DELETE FROM simulation WHERE id = ?").param(old).update();

        UUID replacement = demo.id();

        assertThat(replacement).isNotEqualTo(old);
        assertThat(service.get(replacement).currentTick()).isEqualTo(50);
    }

    @Test
    void servesItsIdOverHttp() throws Exception {
        mvc.perform(get("/api/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(demo.id().toString()));
    }

    @Test
    void nobodyCanChangeIt() throws Exception {
        UUID id = demo.id();

        mvc.perform(get("/api/simulations/{id}", id))
                .andExpect(jsonPath("$.featured").value(true));
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "1"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/simulations/{id}/advance", id).param("toTick", "60"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/simulations/{id}/run", id))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/simulations/{id}/pause", id))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/simulations/{id}/incidents/{incident}/acknowledge", id, UUID.randomUUID()))
                .andExpect(status().isConflict());

        assertThat(service.get(id).currentTick()).isEqualTo(50);
        assertThat(service.get(id).running()).isFalse();
    }

    @Test
    void theClockLeavesItAlone() {
        UUID id = demo.id();
        jdbc.sql("UPDATE simulation SET running = true WHERE id = ?").param(id).update();

        service.clockStep(id);

        assertThat(service.get(id).currentTick()).isEqualTo(50);
    }

    @Test
    void retentionKeepsIt() {
        UUID id = demo.id();
        jdbc.sql("UPDATE simulation SET created_at = now() - interval '2 days' WHERE id = ?").param(id).update();

        tx.execute(status -> repository.deleteCreatedBefore(Instant.now().minusSeconds(86_400)));

        assertThat(service.get(id).currentTick()).isEqualTo(50);
    }
}

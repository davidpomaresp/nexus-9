package com.nexus9.backend.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus9.backend.PostgresTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
}

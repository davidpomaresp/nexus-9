package com.nexus9.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus9.backend.PostgresTestBase;
import com.nexus9.backend.simulation.SimulationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SimulationEventsTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    SimulationService service;

    @Autowired
    SimulationEvents events;

    @Test
    void streamsTheStateOfTheSimulation() throws Exception {
        UUID id = service.create(42, "solar-degradation").id();

        MockHttpServletResponse stream = mvc.perform(get("/api/simulations/{id}/events", id))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
        assertThat(events.subscribers(id)).isEqualTo(1);

        service.advance(id, 5);
        service.run(id, 20);
        service.pause(id);

        String body = stream.getContentAsString();
        assertThat(body).startsWith("event:state");
        assertThat(body).contains("\"currentTick\":0", "\"currentTick\":5", "\"running\":true", "\"speed\":20");
    }

    @Test
    void unknownSimulationsAreNotFound() throws Exception {
        mvc.perform(get("/api/simulations/{id}/events", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/api/simulations/{id}/run", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}

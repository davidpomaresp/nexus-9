package com.nexus9.backend.api;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexus9.backend.PostgresTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SimulationApiTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void createAdvanceAndQuery() throws Exception {
        String id = create(42, "nominal");

        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTick").value(20))
                .andExpect(jsonPath("$.openIncidents").value(0));

        mvc.perform(get("/api/simulations/{id}/modules", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].module").value("POWER"))
                .andExpect(jsonPath("$[0].status").value("NOMINAL"))
                .andExpect(jsonPath("$[0].tick").value(19))
                .andExpect(jsonPath("$[2].values.mode").value("FULL"));

        mvc.perform(get("/api/simulations/{id}/modules/OXYGEN/readings", id)
                        .param("fromTick", "5").param("toTick", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].tick").value(5))
                .andExpect(jsonPath("$[0].values.levelPercent").value(100.0));
    }

    @Test
    void thereIsNoDemoSimulationUnlessConfigured() throws Exception {
        mvc.perform(get("/api/demo")).andExpect(status().isNotFound());
    }

    @Test
    void solarDegradationShowsCriticalModulesAndIncidents() throws Exception {
        String id = create(42, "solar-degradation");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "600"))
                .andExpect(jsonPath("$.openIncidents").value(5));

        mvc.perform(get("/api/simulations/{id}/modules", id))
                .andExpect(jsonPath("$[1].module").value("OXYGEN"))
                .andExpect(jsonPath("$[*].tick", everyItem(is(599))))
                .andExpect(jsonPath("$[0].highestPriority").value("CRITICAL"))
                .andExpect(jsonPath("$[1].status").value("OFFLINE"))
                .andExpect(jsonPath("$[1].highestPriority").value("CRITICAL"))
                .andExpect(jsonPath("$[2].status").value("OFFLINE"))
                .andExpect(jsonPath("$[2].highestPriority").value("MEDIUM"));

        mvc.perform(get("/api/simulations/{id}/incidents", id).param("status", "OPEN"))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].ruleCode").value("GENERATION_DEFICIT"))
                .andExpect(jsonPath("$[0].affectedModules", hasSize(2)));
    }

    @Test
    void advanceToTickIsIdempotent() throws Exception {
        String id = create(42, "nominal");

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/simulations/{id}/advance", id).param("toTick", "15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentTick").value(15));
        }
        mvc.perform(get("/api/simulations/{id}/modules/POWER/readings", id))
                .andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    void readingsOfANewSimulationAreEmpty() throws Exception {
        String id = create(42, "nominal");

        mvc.perform(get("/api/simulations/{id}/modules/POWER/readings", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/simulations/{id}/modules", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NOMINAL"))
                .andExpect(jsonPath("$[0].tick").doesNotExist());
    }

    @Test
    void readingsDefaultToTheLatestWindow() throws Exception {
        String id = create(42, "nominal");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "5100"));

        mvc.perform(get("/api/simulations/{id}/modules/COMMS/readings", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5000)))
                .andExpect(jsonPath("$[0].tick").value(100))
                .andExpect(jsonPath("$[4999].tick").value(5099));
    }

    @Test
    void stateIsReadAtASingleTick() throws Exception {
        String id = create(42, "solar-degradation");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "130"));

        mvc.perform(get("/api/simulations/{id}/state", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation.currentTick").value(130))
                .andExpect(jsonPath("$.simulation.openIncidents").value(1))
                .andExpect(jsonPath("$.modules[*].tick", everyItem(is(129))))
                .andExpect(jsonPath("$.incidents[0].ruleCode").value("GENERATION_DEFICIT"))
                .andExpect(jsonPath("$.incidents[0].openedAtTick").value(120))
                .andExpect(jsonPath("$.readings", hasSize(390)))
                .andExpect(jsonPath("$.readings[0].tick").value(0))
                .andExpect(jsonPath("$.readings[389].tick").value(129));
    }

    @Test
    void stateReturnsOnlyNewReadings() throws Exception {
        String id = create(42, "nominal");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "20"));

        mvc.perform(get("/api/simulations/{id}/state", id).param("fromTick", "18"))
                .andExpect(jsonPath("$.readings", hasSize(6)))
                .andExpect(jsonPath("$.readings[0].tick").value(18))
                .andExpect(jsonPath("$.readings[0].module").value("COMMS"));
        mvc.perform(get("/api/simulations/{id}/state", id).param("fromTick", "20"))
                .andExpect(jsonPath("$.readings", hasSize(0)))
                .andExpect(jsonPath("$.modules", hasSize(3)));
    }

    @Test
    void stateKeepsTheLatestWindow() throws Exception {
        String id = create(42, "nominal");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "5100"));

        mvc.perform(get("/api/simulations/{id}/state", id))
                .andExpect(jsonPath("$.readings", hasSize(15000)))
                .andExpect(jsonPath("$.readings[0].tick").value(100));
    }

    @Test
    void stateOfANewSimulationIsEmpty() throws Exception {
        String id = create(42, "nominal");

        mvc.perform(get("/api/simulations/{id}/state", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation.currentTick").value(0))
                .andExpect(jsonPath("$.readings", hasSize(0)))
                .andExpect(jsonPath("$.incidents", hasSize(0)));
        mvc.perform(get("/api/simulations/{id}/state", id).param("fromTick", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acknowledgeAnIncident() throws Exception {
        String id = create(42, "solar-degradation");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "130"));
        String body = mvc.perform(get("/api/simulations/{id}/incidents", id))
                .andReturn().getResponse().getContentAsString();
        String incident = JsonPath.read(body, "$[0].id");

        mvc.perform(post("/api/simulations/{id}/incidents/{incident}/acknowledge", id, incident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedAtTick").value(129));
        mvc.perform(get("/api/simulations/{id}/state", id))
                .andExpect(jsonPath("$.incidents[0].acknowledgedAtTick").value(129));
        mvc.perform(post("/api/simulations/{id}/incidents/{incident}/acknowledge", id, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void errorsUseProblemDetails() throws Exception {
        mvc.perform(get("/api/simulations/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\": 1, \"scenario\": \"unknown\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        String id = create(1, "nominal");
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/simulations/{id}/advance", id).param("ticks", "1").param("toTick", "5"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/simulations/{id}/modules/FOO/readings", id))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/simulations/{id}/modules/POWER/readings", id)
                        .param("fromTick", "9").param("toTick", "3"))
                .andExpect(status().isBadRequest());
    }

    private String create(long seed, String scenario) throws Exception {
        String body = mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seed\": %d, \"scenario\": \"%s\"}".formatted(seed, scenario)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}

package com.nexus9.backend.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexus9.backend.PostgresTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WebPanelTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void servesThePanel() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NEXUS-9")))
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }

    @Test
    void simulationUrlsOpenThePanel() throws Exception {
        mvc.perform(get("/simulations/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/simulations/not-an-id"))
                .andExpect(status().isNotFound());
    }
}

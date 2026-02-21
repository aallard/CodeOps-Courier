package com.codeops.courier.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for HealthController verifying response structure and public access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/courier/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("codeops-courier"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void health_noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/v1/courier/health"))
                .andExpect(status().isOk());
    }
}

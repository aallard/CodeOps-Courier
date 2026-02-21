package com.codeops.courier.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for SecurityConfig verifying public and protected endpoint access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/courier/health"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUi_isPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void apiEndpoints_requireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/courier/anything"))
                .andExpect(status().isUnauthorized());
    }
}

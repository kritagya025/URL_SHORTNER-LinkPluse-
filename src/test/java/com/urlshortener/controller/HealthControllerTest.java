package com.urlshortener.controller;

import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlRepository urlRepository;

    @Test
    @DisplayName("GET /api/health - Returns 200 OK and UP when database is reachable")
    void getHealth_Healthy_Returns200() throws Exception {
        when(urlRepository.count()).thenReturn(42L);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("CONNECTED"))
                .andExpect(jsonPath("$.service").value("LinkPulse URL Shortener"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/health - Returns 503 Service Unavailable when database throws exception")
    void getHealth_Unhealthy_Returns503() throws Exception {
        when(urlRepository.count()).thenThrow(new RuntimeException("Database connection timeout"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.database").value("DISCONNECTED"))
                .andExpect(jsonPath("$.service").value("LinkPulse URL Shortener"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

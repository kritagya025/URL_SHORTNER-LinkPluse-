package com.urlshortener.controller;

import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests covering how unmatched request paths are reported.
 *
 * <p>An unmatched path is resolved by Spring's static resource handler, which raises
 * NoResourceFoundException. While the global handler had only a catch-all for Exception,
 * every mistyped URL was answered with a 500 instead of a 404.
 */
@WebMvcTest(controllers = {UrlController.class, RedirectController.class})
class UnknownPathHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    @DisplayName("Unknown static asset returns 404, not 500")
    void unknownStaticAsset_Returns404() throws Exception {
        mockMvc.perform(get("/does-not-exist.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Unknown nested path returns 404, not 500")
    void unknownNestedPath_Returns404() throws Exception {
        mockMvc.perform(get("/some/deep/missing/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Unknown API sub-path returns 404, not 500")
    void unknownApiPath_Returns404() throws Exception {
        mockMvc.perform(get("/api/urls/unknown/bogus"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Error responses do not leak internal exception detail")
    void errorResponse_DoesNotLeakInternals() throws Exception {
        mockMvc.perform(get("/does-not-exist.png"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"));
    }
}

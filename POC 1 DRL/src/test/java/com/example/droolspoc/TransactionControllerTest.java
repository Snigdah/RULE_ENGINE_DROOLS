package com.example.droolspoc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void devirAbove5000ReturnsPermissionDenied() throws Exception {
        mockMvc.perform(post("/api/transactions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferMode\":\"DEVIR\",\"amount\":6000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.permissionDenied").value(true));
    }

    @Test
    void missingAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transferMode\":\"NORMAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }
}

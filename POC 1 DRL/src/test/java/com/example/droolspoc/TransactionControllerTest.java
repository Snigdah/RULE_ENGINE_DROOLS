package com.example.droolspoc;

import com.example.droolspoc.controller.TransactionController;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.service.TransactionRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionRuleService service;

    @Test
    void blockedTransactionReturnsFlags() throws Exception {
        when(service.validate(any()))
                .thenReturn(new TransactionResponse(false, true, "Blocked: ..."));

        mockMvc.perform(post("/api/transactions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER-001\",\"transactionMode\":\"TRANSFER\","
                                + "\"debitCredit\":\"DR\",\"sourceAccount\":\"100001\","
                                + "\"currency\":\"BDT\",\"amount\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.permissionDenied").value(true));
    }

    @Test
    void missingAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER-001\",\"transactionMode\":\"TRANSFER\","
                                + "\"debitCredit\":\"DR\",\"sourceAccount\":\"100001\","
                                + "\"currency\":\"BDT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }
}

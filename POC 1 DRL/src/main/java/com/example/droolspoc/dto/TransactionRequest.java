package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Incoming API payload. Input fields only — no rule-result state.
 */
@Getter
@Setter
public class TransactionRequest {

    private String sourceAccount;
    private String sourceBranch;
    private String destinationAccount;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    private String debitCredit;
    private String currency;
    private BigDecimal exchangeRate;
    private String remarks;
    private String userId;

    @NotBlank(message = "transferMode is required")
    private String transferMode;
}

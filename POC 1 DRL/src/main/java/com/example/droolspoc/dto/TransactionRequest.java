package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Incoming API payload. Input only. The fields marked required are the keys
 * used to look up UserLimit and Product.
 */
@Getter
@Setter
public class TransactionRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "transactionMode is required")
    private String transactionMode;   // TRANSFER | CREDIT

    @NotBlank(message = "debitCredit is required")
    private String debitCredit;       // DR | CR

    @NotBlank(message = "sourceAccount is required")
    private String sourceAccount;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    // Optional / informational
    private String sourceBranch;
    private String destinationAccount;
    private BigDecimal exchangeRate;
    private String remarks;

    // Risk / compliance inputs (used by VELOCITY and COMPLIANCE rules)
    private String channel;          // ATM | POS | ONLINE | BRANCH | AGENT
    private String country;          // BD, US, UK, AE, SG, OTHER
    private Integer dailyTxnCount;   // transfers the user already made today
}

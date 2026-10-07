package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Transfer input fact (inside {@link ValidationContext}). Input only - the decision result
 * now lives on {@link RuleContext}, where every flow writes it the same way.
 */
@Getter
@Setter
public class Transaction {

    private String sourceAccount;
    private String sourceBranch;
    private String destinationAccount;
    private BigDecimal amount;
    private String debitCredit;      // DR | CR
    private String currency;
    private BigDecimal exchangeRate;
    private String remarks;
    private String userId;
    private String transactionMode;  // TRANSFER | CREDIT
}

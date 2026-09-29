package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Internal input holder built from the request (used inside ValidationContext). */
@Getter
@Setter
public class Transaction {

    private String sourceAccount;
    private String sourceBranch;
    private String destinationAccount;
    private BigDecimal amount;
    private String debitCredit;
    private String currency;
    private BigDecimal exchangeRate;
    private String remarks;
    private String userId;
    private String transactionMode;
}

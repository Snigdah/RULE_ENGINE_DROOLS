package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Internal Drools fact (inside ValidationContext). The rules read the input
 * fields and mutate the result fields. Never exposed at the API boundary.
 */
@Getter
@Setter
public class Transaction {

    // Input
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

    // Rule result fields (written by the rules)
    private boolean valid = true;
    private boolean permissionDenied = false;
    private String validationMessage;
}

package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Internal Drools fact inserted into the KieSession. The rules read the input
 * fields and mutate the result fields. Not exposed at the API boundary — see
 * TransactionRequest / TransactionResponse for the API contract.
 */
@Getter
@Setter
public class Transaction {

    // Input
    private String sourceAccount;
    private String sourceBranch;
    private String destinationAccount;
    private BigDecimal amount;
    private String debitCredit;
    private String currency;
    private BigDecimal exchangeRate;
    private String remarks;
    private String userId;
    private String transferMode;

    // Rule result fields (written by the rules)
    private boolean valid = true;
    private boolean permissionDenied = false;
    private String validationMessage;
}

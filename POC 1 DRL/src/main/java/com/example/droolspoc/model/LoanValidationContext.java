package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Loan-flow fact. Extends {@link RuleContext} and adds the loan-specific inputs the LOAN rule
 * reads: loan, customer.
 */
@Getter
@Setter
public class LoanValidationContext extends RuleContext {

    private Loan loan;
    private Customer customer;
}

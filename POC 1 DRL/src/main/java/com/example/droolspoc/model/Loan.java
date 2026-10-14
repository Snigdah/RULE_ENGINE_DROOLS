package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Loan input fact (inside {@link LoanValidationContext}). Built from the loan request.
 */
@Getter
@Setter
public class Loan {

    private String userId;
    private BigDecimal amount;
    private Integer tenureMonths;
    private String purpose;
}

package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Loan application payload. {@code userId} looks up the Customer (credit score) and User (KYC).
 */
@Getter
@Setter
public class LoanRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    private Integer tenureMonths;
    private String purpose;
}

package com.example.droolspoc.dto;

/** Loan rule outcome. */
public record LoanResponse(
        boolean valid,
        boolean permissionDenied,
        String message
) {
}

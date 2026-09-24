package com.example.droolspoc.dto;

/**
 * Outgoing API payload. The rule outcome only.
 */
public record TransactionResponse(
        boolean valid,
        boolean permissionDenied,
        String message
) {
}

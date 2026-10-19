package com.example.droolspoc.dto;

/** Account-closure rule outcome. */
public record AccountCloseResponse(
        boolean valid,
        boolean permissionDenied,
        String message
) {
}

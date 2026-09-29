package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload to upload/update a DRL file by JSON.
 */
public record RuleUploadRequest(
        @NotBlank(message = "fileName is required") String fileName,
        @NotBlank(message = "drl is required") String drl
) {
}

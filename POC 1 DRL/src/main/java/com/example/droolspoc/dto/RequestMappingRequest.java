package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotBlank;

/** Map a request DTO class to a flow. */
public record RequestMappingRequest(
        @NotBlank(message = "requestClass is required") String requestClass,
        @NotBlank(message = "flowName is required") String flowName
) {
}

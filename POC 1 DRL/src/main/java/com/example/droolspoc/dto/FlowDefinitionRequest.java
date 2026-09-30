package com.example.droolspoc.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Define a flow and its ordered agenda groups. */
public record FlowDefinitionRequest(
        @NotBlank(message = "flowName is required") String flowName,
        @NotEmpty(message = "groups is required (at least one)") List<String> groups
) {
}

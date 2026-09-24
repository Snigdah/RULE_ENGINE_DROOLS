package com.example.droolspoc.model;

import lombok.Data;

/**
 * Single fact the rules operate on. Assembled by the service from the request
 * plus the two DB lookups, so all three parts are guaranteed non-null when the
 * rules run.
 */
@Data
public class ValidationContext {

    private Transaction transaction;  // built from the request; holds the result fields
    private UserLimit userLimit;      // loaded from DB
    private Product product;          // loaded from DB
}

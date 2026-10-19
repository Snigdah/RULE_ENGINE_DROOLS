package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Transfer-flow fact. Extends {@link RuleContext} (userId, user, result) and adds the
 * transfer-specific inputs the TRANSFER rule reads: transaction, userLimit, product.
 */
@Getter
@Setter
public class ValidationContext extends RuleContext {

    private Transaction transaction;
    private UserLimit userLimit;
    private Product product;
}

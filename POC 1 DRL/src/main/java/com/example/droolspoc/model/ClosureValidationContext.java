package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Account-closure-flow fact. Extends {@link RuleContext} and adds the account the CLOSURE
 * rule reads.
 */
@Getter
@Setter
public class ClosureValidationContext extends RuleContext {

    private Account account;
}

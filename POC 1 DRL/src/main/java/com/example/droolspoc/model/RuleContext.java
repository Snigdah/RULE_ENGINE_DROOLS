package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Shared base for every flow's context (transfer, loan, closure).
 *
 * <p>It carries the data every flow shares - the {@code userId} and the {@code user}. A rule
 * written against {@code RuleContext} (for example in the shared COMMON group) fires for all
 * three concrete contexts.</p>
 *
 * <p>It holds <b>no decision fields</b>: the library returns the decision as a
 * {@code leads.ruleengine.core.context.RuleResult} from {@code ruleEngine.execute(..)}.</p>
 */
@Getter
@Setter
public abstract class RuleContext {

    /** The acting user id. */
    private String userId;

    /** The acting user (e.g. {@code user.kycVerified}). */
    private User user;
}

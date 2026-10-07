package com.example.droolspoc.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Shared base for every flow's context (transfer, loan, closure).
 *
 * <p>It carries the two things the cross-cutting COMMON rules need - the {@code userId}
 * (blocked-user check) and the {@code user} (KYC check) - plus the decision result the rules
 * write back. Because all three concrete contexts extend this, a COMMON rule written against
 * {@code RuleContext} fires for ALL of them: that is how one rule is reused across flows.</p>
 *
 * <p>The library's generated DRL sets the outcome on the inserted fact ({@code $ctx.setValid(...)}),
 * so keeping the result here means hand-written and builder-generated rules use the same calls.</p>
 */
@Getter
@Setter
public abstract class RuleContext {

    /** The acting user id - read by the COMMON blocked-user rule. */
    private String userId;

    /** The acting user - the COMMON KYC rule reads {@code user.kycVerified}. */
    private User user;

    // ---- decision result (the rules write these) ----
    private boolean valid = true;
    private boolean permissionDenied = false;
    private String validationMessage;
}

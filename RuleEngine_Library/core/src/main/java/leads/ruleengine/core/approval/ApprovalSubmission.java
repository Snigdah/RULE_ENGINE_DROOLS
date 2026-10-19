package leads.ruleengine.core.approval;

/**
 * Response for an admin write that was submitted for approval instead of being applied
 * (HTTP 202). Nothing in the rule tables has changed yet; the change is applied only when the
 * checker gives final approval.
 *
 * @param setId    changeboard approval set id ({@code TREG_APPROVAL.SET_ID}); may be null if it
 *                 could not be read back
 * @param status   always {@code PENDING_APPROVAL}
 * @param action   CREATE / UPDATE / DELETE
 * @param target   what was submitted, e.g. {@code RULE} or {@code GROUP}
 * @param targetId id of the rule/group (null for a create - the id is assigned on approval)
 * @param message  human-readable summary for the UI
 *
 * @author K M Farhat Snigdah
 */
public record ApprovalSubmission(
        Long setId,
        String status,
        String action,
        String target,
        Long targetId,
        String message) {

    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
}

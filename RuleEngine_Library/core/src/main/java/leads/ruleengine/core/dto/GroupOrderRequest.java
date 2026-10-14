package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * JSON body for reordering a flow's groups ({@code PUT /admin/flows/{flowCode}/groups/order}).
 * The list is the group ids in the desired firing order; {@code order_no} is rewritten to match.
 *
 * @author K M Farhat Snigdah
 */
public record GroupOrderRequest(
        @NotEmpty List<Long> groupIds) {
}

package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for adding an agenda group to a flow ({@code POST /admin/flows/{flowCode}/groups}).
 * {@code orderNo} is optional - when omitted the group is appended after the flow's existing groups.
 *
 * @author K M Farhat Snigdah
 */
public record GroupRequest(
        @NotBlank String groupCode,
        Integer orderNo) {
}

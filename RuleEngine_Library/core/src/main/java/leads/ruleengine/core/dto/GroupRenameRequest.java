package leads.ruleengine.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for renaming a group ({@code PUT /admin/groups/{groupId}/code}). The code is the
 * Drools agenda-group name; it must stay unique within the flow (or among shared groups).
 *
 * @author K M Farhat Snigdah
 */
public record GroupRenameRequest(@NotBlank String groupCode) {
}

package leads.ruleengine.core.approval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The checker's approve / decline / send-back action for rule-engine approval sets.
 *
 * <p>Same path and response as Sentinel's {@code ApprovalRegisterController}, so the Sentinel
 * approval screen only has to switch the base URL for sets whose class is a rule-engine entity.
 * The pending list itself still comes from Sentinel (both services share the TREG_* tables).</p>
 *
 * <p>{@code approvalFlag}: 1 = approve, 2 = decline, 3 = send back, 4 = discard (changeboard's
 * {@code ApprovalStatus}). Decline / send back / discard need remarks.</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/ApprovalRegister")
@ConditionalOnProperty(prefix = "rule-engine.approval", name = "enabled", havingValue = "true")
public class RuleApprovalController {

    private final RuleApprovalProcessor processor;

    public RuleApprovalController(RuleApprovalProcessor processor) {
        this.processor = processor;
    }

    @GetMapping("/ExecuteApprovalOperation/{officeId}/{appId}/{setId}/{approvalLevel}/"
            + "{nextApprovalLevel}/{remarks}/{userId}/{approvalFlag}")
    public ResponseEntity<String> executeApprovalOperation(
            @PathVariable("officeId") Long officeId,
            @PathVariable("appId") Long appId,
            @PathVariable("setId") Long setId,
            @PathVariable("approvalLevel") Integer approvalLevel,
            @PathVariable("nextApprovalLevel") Integer nextApprovalLevel,
            @PathVariable("remarks") String remarks,
            @PathVariable("userId") String userId,
            @PathVariable("approvalFlag") Integer approvalFlag) {
        processor.process(officeId, appId, setId, approvalLevel, nextApprovalLevel, remarks, userId, approvalFlag);
        return ResponseEntity.ok("Operation completed successfully");
    }
}

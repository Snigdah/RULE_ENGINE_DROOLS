package leads.ruleengine.core.approval;

import com.leads.changeboard.hibernate.ApprovalHibernateService;
import com.leads.model.ActionType;
import com.leads.model.ApprovalBaseEntity;
import com.leads.model.ApprovalStatus;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Hands a prepared (detached, NOT saved) rule or group to changeboard's Hibernate approval path.
 *
 * <p>Callers build a fresh copy of the entity with the requested change applied and never touch
 * the managed row, so nothing reaches the rule tables until final approval - changeboard applies
 * it then. Must run inside a transaction: changeboard queues approver lookup after commit.</p>
 *
 * @author K M Farhat Snigdah
 */
public class ChangeApprovalSubmitter {

    private static final Logger log = LoggerFactory.getLogger(ChangeApprovalSubmitter.class);

    /** Table names as changeboard records them in {@code TREG_APPROVAL_TABLES.TABLE_NM}. */
    public static final String RULE_TABLE = "RULE_DEFINITION";
    public static final String GROUP_TABLE = "RULE_GROUP";

    /** {@code TREG_APPROVAL.APPROVE_FLAG} value for a set still waiting on approvers. */
    private static final int PENDING = ApprovalStatus.PENDING.getValue();

    private final ApprovalHibernateService approvalHibernateService;
    private final ApprovalRequestContext requestContext;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public ChangeApprovalSubmitter(ApprovalHibernateService approvalHibernateService,
                                   ApprovalRequestContext requestContext,
                                   JdbcTemplate jdbcTemplate,
                                   EntityManager entityManager) {
        this.approvalHibernateService = approvalHibernateService;
        this.requestContext = requestContext;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    /** The maker of the current request (also written to the rule's CREATED_BY). */
    public String currentUser() {
        return requestContext.requireUserId();
    }

    /**
     * Submit one prepared entity (with any aggregate children) for approval.
     *
     * @param entity   detached copy carrying the requested change
     * @param action   CREATE / UPDATE / DELETE
     * @param target   label for the response, e.g. {@code RULE}
     * @param targetId id of the row (null for a create)
     * @param summary  short description, stored as the approval remarks
     */
    public ApprovalSubmission submit(ApprovalBaseEntity entity, ActionType action,
                                     String target, Long targetId, String summary) {
        String user = requestContext.requireUserId();
        Long appId = requestContext.appId();
        Long functionId = requestContext.functionId();

        entity.setRecordUserId(user);
        entity.setRecordDt(LocalDateTime.now());
        entity.setActionType(action);
        entity.setApproveFlag(ApprovalStatus.PENDING);
        entity.setRemarks(summary);

        approvalHibernateService.sendForApprovalHibernate(appId, functionId, entity);

        Long setId = latestSetId(functionId, user);
        log.info("Submitted for approval: {} {} {} (setId={}, maker={}, app={}, function={}) - {}",
                action, target, targetId == null ? "(new)" : targetId, setId, user, appId, functionId, summary);
        return new ApprovalSubmission(setId, ApprovalSubmission.PENDING_APPROVAL, action.name(), target, targetId,
                summary + " - submitted for approval.");
    }

    /**
     * Refuse a second change while the row already sits in a pending approval set (directly, or
     * as a child of a pending group change).
     *
     * @param table the business table, e.g. {@code RULE_DEFINITION}
     * @param ids   primary keys to check
     */
    public void assertNotPending(String table, Collection<Long> ids) {
        List<Long> nonNull = ids.stream().filter(java.util.Objects::nonNull).toList();
        for (Long id : nonNull) {
            Long setId = jdbcTemplate.query(
                    "SELECT MIN(a.SET_ID) FROM TREG_APPROVAL_TABLES t "
                            + "JOIN TREG_APPROVAL a ON a.SET_ID = t.SET_ID "
                            + "WHERE a.APPROVE_FLAG = ? AND t.TABLE_NM = ? AND t.PK_COLUMN_VAL = ?",
                    rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null,
                    PENDING, table, String.valueOf(id));
            if (setId != null) {
                throw new PendingApprovalException(table + " id " + id
                        + " already has a change waiting for approval (setId " + setId
                        + "). Approve, decline or send it back first.");
            }
        }
    }

    /** The set changeboard just created for this maker (same transaction, so flush first). */
    private Long latestSetId(Long functionId, String user) {
        try {
            entityManager.flush();
            return jdbcTemplate.queryForObject(
                    "SELECT MAX(SET_ID) FROM TREG_APPROVAL WHERE FUNCTION_ID = ? AND RECORD_USER_ID = ?",
                    Long.class, functionId, user);
        } catch (RuntimeException e) {
            log.warn("Approval submitted, but its setId could not be read back: {}", e.getMessage());
            return null;
        }
    }
}

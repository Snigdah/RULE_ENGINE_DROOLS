package leads.ruleengine.core.exception;

import com.leads.exceptionHandler.ResourceNotFoundException;
import leads.ruleengine.core.approval.PendingApprovalException;
import leads.ruleengine.core.approval.RuleApprovalController;
import leads.ruleengine.core.controller.FlowAdminController;
import leads.ruleengine.core.controller.RuleAdminController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error handling SCOPED to the rule-engine admin controllers only.
 *
 * <p><b>Why scoped:</b> a library must not install a global {@code @RestControllerAdvice} -
 * that would silently hijack error handling for the consumer's own controllers. Binding it
 * with {@code assignableTypes} limits it to this library's admin endpoints; the consumer's
 * exception handling is left completely alone.</p>
 *
 * @author K M Farhat Snigdah
 */
@RestControllerAdvice(assignableTypes = {RuleAdminController.class, FlowAdminController.class,
        RuleApprovalController.class})
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleEngineAdminExceptionHandler {

    /** A DRL that failed to compile -> 400, engine untouched. */
    @ExceptionHandler(RuleCompilationException.class)
    public ResponseEntity<Map<String, Object>> handleCompilation(RuleCompilationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Missing flow/group referenced from an admin call -> 400. */
    @ExceptionHandler(FlowNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleFlowNotConfigured(FlowNotConfiguredException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** A group still holds rules and was not force-deleted -> 409. */
    @ExceptionHandler(GroupInUseException.class)
    public ResponseEntity<Map<String, Object>> handleGroupInUse(GroupInUseException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** The rule/group already has a change waiting for approval -> 409. */
    @ExceptionHandler(PendingApprovalException.class)
    public ResponseEntity<Map<String, Object>> handlePendingApproval(PendingApprovalException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** changeboard rejected the submission or decision (e.g. bad approval payload) -> 400. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleChangeboard(ResourceNotFoundException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Bad admin input (unknown id, missing sourceJson/drl, duplicate name, ...) -> 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Bean-validation failures on admin DTOs -> 400 with field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation failed");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(baseBody(status, message));
    }

    private Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}

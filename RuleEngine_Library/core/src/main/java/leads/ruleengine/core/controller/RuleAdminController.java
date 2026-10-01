package leads.ruleengine.core.controller;

import jakarta.validation.Valid;
import leads.ruleengine.core.dto.RuleUploadRequest;
import leads.ruleengine.core.model.RuleFile;
import leads.ruleengine.core.service.RuleAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Runtime rule management. Upload a DRL (JSON or file), list stored rules, deactivate one.
 * A bad DRL is rejected (400) without disturbing the live engine.
 *
 * <p>Exposed only when {@code rule-engine.admin.enabled} is true (the default). Set it to
 * {@code false} in a service that wants rules managed elsewhere, or secure this path with
 * the AuthZ library.</p>
 *
 * @author K M Farhat Snigdah
 */
@RestController
@RequestMapping("/admin/rules")
@ConditionalOnProperty(prefix = "rule-engine.admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleAdminController {

    private final RuleAdminService ruleAdminService;

    public RuleAdminController(RuleAdminService ruleAdminService) {
        this.ruleAdminService = ruleAdminService;
    }

    @GetMapping
    public List<RuleFile> list() {
        return ruleAdminService.list();
    }

    /** Upload/replace a DRL as JSON: {@code { "fileName": "...", "drl": "..." }}. */
    @PostMapping
    public ResponseEntity<RuleFile> upload(@Valid @RequestBody RuleUploadRequest request) {
        return ResponseEntity.ok(ruleAdminService.upsert(request.fileName(), request.drl()));
    }

    /** Upload/replace a DRL as a multipart file (fileName defaults to the uploaded name). */
    @PostMapping("/upload")
    public ResponseEntity<RuleFile> uploadFile(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "fileName", required = false) String fileName)
            throws IOException {
        String name = (fileName != null && !fileName.isBlank()) ? fileName : file.getOriginalFilename();
        String drl = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(ruleAdminService.upsert(name, drl));
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> delete(@PathVariable String fileName) {
        ruleAdminService.delete(fileName);
        return ResponseEntity.noContent().build();
    }
}

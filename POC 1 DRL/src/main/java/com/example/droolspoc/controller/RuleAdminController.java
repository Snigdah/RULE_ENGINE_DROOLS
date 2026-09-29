package com.example.droolspoc.controller;

import com.example.droolspoc.dto.RuleUploadRequest;
import com.example.droolspoc.service.RuleAdminService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import java.util.Map;

/**
 * Admin endpoints to manage DRL files at runtime. Every change validates,
 * saves, and reloads automatically - no restart.
 *
 * NOTE: DRL is executable code. In production this must be locked down to
 * authorized users; that access layer is out of POC scope.
 */
@RestController
@RequestMapping("/admin/rules")
public class RuleAdminController {

    private final RuleAdminService ruleAdminService;

    public RuleAdminController(RuleAdminService ruleAdminService) {
        this.ruleAdminService = ruleAdminService;
    }

    /** Upload/update by JSON body. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> upsert(@Valid @RequestBody RuleUploadRequest request) {
        ruleAdminService.upsert(request.fileName(), request.drl());
        return ResponseEntity.ok(Map.of("status", "reloaded", "fileName", request.fileName()));
    }

    /** Upload/update by uploading a .drl file (multipart). */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file)
            throws IOException {

        String fileName = file.getOriginalFilename();
        String drl = new String(file.getBytes(), StandardCharsets.UTF_8);
        ruleAdminService.upsert(fileName, drl);
        return ResponseEntity.ok(Map.of("status", "reloaded", "fileName", fileName));
    }

    /** Deactivate a rule file. */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String fileName) {
        ruleAdminService.delete(fileName);
        return ResponseEntity.ok(Map.of("status", "reloaded", "fileName", fileName));
    }

    /** List all rule files (metadata). */
    @GetMapping
    public List<Map<String, Object>> list() {
        return ruleAdminService.list().stream()
                .<Map<String, Object>>map(r -> Map.of(
                        "fileName", r.getFileName(),
                        "active", r.isActive(),
                        "version", r.getVersion(),
                        "updatedAt", r.getUpdatedAt() == null ? "" : r.getUpdatedAt().toString()))
                .toList();
    }
}

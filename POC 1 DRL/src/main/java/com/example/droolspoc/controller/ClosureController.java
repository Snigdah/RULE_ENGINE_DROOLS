package com.example.droolspoc.controller;

import com.example.droolspoc.dto.AccountCloseRequest;
import com.example.droolspoc.dto.AccountCloseResponse;
import com.example.droolspoc.service.ClosureRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account-closures")
public class ClosureController {

    private final ClosureRuleService closureRuleService;

    public ClosureController(ClosureRuleService closureRuleService) {
        this.closureRuleService = closureRuleService;
    }

    @PostMapping("/validate")
    public ResponseEntity<AccountCloseResponse> validate(@Valid @RequestBody AccountCloseRequest request) {
        return ResponseEntity.ok(closureRuleService.validate(request));
    }
}

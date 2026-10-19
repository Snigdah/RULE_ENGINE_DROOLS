package com.example.droolspoc.controller;

import com.example.droolspoc.dto.LoanRequest;
import com.example.droolspoc.dto.LoanResponse;
import com.example.droolspoc.service.LoanRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanRuleService loanRuleService;

    public LoanController(LoanRuleService loanRuleService) {
        this.loanRuleService = loanRuleService;
    }

    @PostMapping("/validate")
    public ResponseEntity<LoanResponse> validate(@Valid @RequestBody LoanRequest request) {
        return ResponseEntity.ok(loanRuleService.validate(request));
    }
}

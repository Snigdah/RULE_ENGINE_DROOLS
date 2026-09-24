package com.example.droolspoc.controller;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.service.TransactionRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRuleService transactionRuleService;

    public TransactionController(TransactionRuleService transactionRuleService) {
        this.transactionRuleService = transactionRuleService;
    }

    @PostMapping("/validate")
    public ResponseEntity<TransactionResponse> validate(
            @Valid @RequestBody TransactionRequest request) {

        return ResponseEntity.ok(transactionRuleService.validate(request));
    }
}

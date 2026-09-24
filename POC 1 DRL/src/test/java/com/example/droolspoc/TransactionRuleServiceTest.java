package com.example.droolspoc;

import com.example.droolspoc.dto.TransactionRequest;
import com.example.droolspoc.dto.TransactionResponse;
import com.example.droolspoc.service.TransactionRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TransactionRuleServiceTest {

    @Autowired
    private TransactionRuleService service;

    private TransactionRequest req(String mode, String amount) {
        TransactionRequest r = new TransactionRequest();
        r.setTransferMode(mode);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void devirAbove5000IsDenied() {
        TransactionResponse res = service.validate(req("DEVIR", "6000"));
        // Both rules match; without salience/halt the message is order-dependent,
        // but the deny flags are always set by the DEVIR rule.
        assertFalse(res.valid());
        assertTrue(res.permissionDenied());
    }

    @Test
    void amountAbove500IsFlaggedButValid() {
        TransactionResponse res = service.validate(req("NORMAL", "800"));
        assertTrue(res.valid());
        assertFalse(res.permissionDenied());
        assertEquals("Transaction amount is greater than 500", res.message());
    }

    @Test
    void smallAmountFiresNothing() {
        TransactionResponse res = service.validate(req("NORMAL", "100"));
        assertTrue(res.valid());
        assertFalse(res.permissionDenied());
    }
}

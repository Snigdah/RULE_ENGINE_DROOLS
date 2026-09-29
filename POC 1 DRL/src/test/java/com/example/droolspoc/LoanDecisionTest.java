package com.example.droolspoc;

import com.example.droolspoc.config.DmnConfig;
import com.example.droolspoc.service.DmnEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNRuntime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Proves the SECOND model (loan) runs through the same generic engine.
 */
class LoanDecisionTest {

    private static final String NS = "https://leads-bd.com/dmn/loan";
    private static final String MODEL = "LoanValidation";
    private static final List<String> RULES = List.of("loanEligibilityRule");

    private DmnEngine dmnEngine;

    @BeforeEach
    void setUp() {
        DMNRuntime dmnRuntime = new DmnConfig().dmnRuntime();
        dmnEngine = new DmnEngine(dmnRuntime);
    }

    private String evaluate(int age, int income) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("age", age);
        inputs.put("monthlyIncome", income);
        Map<String, Object> out = dmnEngine.evaluate(NS, MODEL, RULES, inputs);
        return (String) out.get("loanEligibilityRule");
    }

    @Test
    void underageIsRejected() {
        assertEquals("Rejected: applicant must be 18 or older", evaluate(16, 50000));
    }

    @Test
    void lowIncomeIsRejected() {
        assertEquals("Rejected: monthly income must be at least 30000", evaluate(25, 20000));
    }

    @Test
    void eligibleReturnsNull() {
        assertNull(evaluate(25, 50000));
    }
}

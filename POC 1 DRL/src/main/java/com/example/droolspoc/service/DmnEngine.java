package com.example.droolspoc.service;

import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Generic DMN engine. A service passes the model, the inputs, and the exact
 * rules (decision names) it wants to run - so different services can run
 * different subsets of the rules in the same model.
 */
@Service
public class DmnEngine {

    private final DMNRuntime dmnRuntime;

    public DmnEngine(DMNRuntime dmnRuntime) {
        this.dmnRuntime = dmnRuntime;
    }

    public Map<String, Object> evaluate(String namespace,
                                        String modelName,
                                        List<String> ruleNames,
                                        Map<String, Object> inputs) {
        DMNModel model = dmnRuntime.getModel(namespace, modelName);
        if (model == null) {
            throw new IllegalStateException("DMN model not found: " + modelName);
        }

        DMNContext dmnContext = dmnRuntime.newContext();
        inputs.forEach(dmnContext::set);

        // Run ONLY the requested decisions (rules).
        DMNResult result = dmnRuntime.evaluateByName(
                model, dmnContext, ruleNames.toArray(new String[0]));
        if (result.hasErrors()) {
            throw new IllegalStateException("DMN error in " + modelName + ": " + result.getMessages());
        }
        return result.getContext().getAll();   // each rule's output, keyed by rule name
    }
}

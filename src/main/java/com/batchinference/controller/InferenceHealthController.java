package com.batchinference.controller;

import com.batchinference.config.InferenceProperties;
import com.batchinference.inference.InferenceClient;
import com.batchinference.inference.InferenceException;
import com.batchinference.model.PromptItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic endpoint for inference configuration (does not expose secrets).
 */
@RestController
public class InferenceHealthController {

    private final InferenceProperties inferenceProperties;
    private final InferenceClient inferenceClient;

    public InferenceHealthController(InferenceProperties inferenceProperties, InferenceClient inferenceClient) {
        this.inferenceProperties = inferenceProperties;
        this.inferenceClient = inferenceClient;
    }

    @GetMapping("/health/inference")
    public Map<String, Object> inferenceHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", inferenceProperties.getProvider());
        body.put("model", inferenceProperties.getModel());
        body.put("baseUrl", inferenceProperties.getBaseUrl());
        body.put("apiKeyConfigured", inferenceProperties.getApiKey() != null
                && !inferenceProperties.getApiKey().isBlank());

        if ("mock".equalsIgnoreCase(inferenceProperties.getProvider())) {
            body.put("status", "MOCK");
            return body;
        }

        if (!Boolean.TRUE.equals(body.get("apiKeyConfigured"))) {
            body.put("status", "MISSING_API_KEY");
            body.put("hint", "Set INFERENCE_API_KEY to a DigitalOcean Gradient model access key");
            return body;
        }

        try {
            inferenceClient.complete(new PromptItem("health-probe", "Say OK"));
            body.put("status", "OK");
        } catch (InferenceException ex) {
            body.put("status", "FAILED");
            body.put("httpStatus", ex.getStatusCode());
            body.put("error", ex.getMessage());
            body.put("hint", "Use a model access key (not DO API token dop_v1_*) and a valid INFERENCE_MODEL slug");
        }

        return body;
    }
}

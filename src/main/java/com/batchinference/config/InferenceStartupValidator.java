package com.batchinference.config;

import com.batchinference.inference.InferenceClient;
import com.batchinference.inference.InferenceException;
import com.batchinference.model.PromptItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs inference configuration at startup and performs a single probe call
 * so auth/model issues appear in App Platform logs before a batch is submitted.
 */
@Component
public class InferenceStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InferenceStartupValidator.class);

    private final InferenceProperties inferenceProperties;
    private final InferenceClient inferenceClient;

    public InferenceStartupValidator(InferenceProperties inferenceProperties, InferenceClient inferenceClient) {
        this.inferenceProperties = inferenceProperties;
        this.inferenceClient = inferenceClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean keyConfigured = inferenceProperties.getApiKey() != null
                && !inferenceProperties.getApiKey().isBlank();

        log.info(
                "Inference config: provider={}, model={}, baseUrl={}, apiKeyConfigured={}",
                inferenceProperties.getProvider(),
                inferenceProperties.getModel(),
                inferenceProperties.getBaseUrl(),
                keyConfigured
        );

        if (!"mock".equalsIgnoreCase(inferenceProperties.getProvider()) && !keyConfigured) {
            log.error(
                    "INFERENCE_API_KEY is not set. Live inference calls will fail. "
                            + "Set a DigitalOcean Gradient *model access key* (not the DO API token) in App Platform."
            );
            return;
        }

        if ("mock".equalsIgnoreCase(inferenceProperties.getProvider())) {
            return;
        }

        try {
            inferenceClient.complete(new PromptItem("startup-probe", "Reply with OK"));
            log.info("Inference probe succeeded for model {}", inferenceProperties.getModel());
        } catch (InferenceException ex) {
            log.error(
                    "Inference probe failed: {} (status={}). "
                            + "Verify INFERENCE_API_KEY is a model access key and INFERENCE_MODEL exists in your catalog.",
                    ex.getMessage(),
                    ex.getStatusCode()
            );
        }
    }
}

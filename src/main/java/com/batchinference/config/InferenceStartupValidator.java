package com.batchinference.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs inference configuration at startup. Live probing is handled by
 * {@code GET /health/inference} so startup stays fast on small App Platform instances.
 */
@Component
public class InferenceStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InferenceStartupValidator.class);

    private final InferenceProperties inferenceProperties;

    public InferenceStartupValidator(InferenceProperties inferenceProperties) {
        this.inferenceProperties = inferenceProperties;
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
        }
    }
}

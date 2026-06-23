package com.batchinference.retry;

import com.batchinference.config.AppProperties;
import com.batchinference.inference.InferenceClient;
import com.batchinference.inference.InferenceException;
import com.batchinference.model.PromptItem;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Wraps {@link InferenceClient} calls with exponential backoff and random jitter.
 * <p>
 * Retryable failures (HTTP 429, 5xx, network errors) are retried up to {@link AppProperties#getMaxRetries()}.
 * Non-retryable client errors (4xx) propagate immediately so the row can be marked failed.
 */
@Component
public class BackoffExecutor {

    private final AppProperties appProperties;

    public BackoffExecutor(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Executes inference with automatic retries on retryable upstream failures.
     *
     * @param client the inference backend to call
     * @param item   prompt record to process
     * @return model response text
     * @throws InferenceException when all retries are exhausted or the error is non-retryable
     */
    public String executeWithRetry(InferenceClient client, PromptItem item) throws InferenceException {
        int maxRetries = appProperties.getMaxRetries();
        long backoffMs = appProperties.getInitialBackoffMs();
        InferenceException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return client.complete(item);
            } catch (InferenceException ex) {
                lastException = ex;
                if (!ex.isRetryable() || attempt == maxRetries) {
                    throw ex;
                }
                sleepWithJitter(backoffMs);
                backoffMs = Math.min(backoffMs * 2, appProperties.getMaxBackoffMs());
            }
        }
        throw lastException;
    }

    private void sleepWithJitter(long backoffMs) throws InferenceException {
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, backoffMs / 2));
        long sleepMs = backoffMs + jitter;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InferenceException("Retry sleep interrupted", ex, false);
        }
    }
}

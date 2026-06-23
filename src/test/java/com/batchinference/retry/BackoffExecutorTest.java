package com.batchinference.retry;

import com.batchinference.config.AppProperties;
import com.batchinference.inference.InferenceClient;
import com.batchinference.inference.InferenceException;
import com.batchinference.model.PromptItem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackoffExecutorTest {

    @Test
    void retriesRetryableErrorsUntilSuccess() throws Exception {
        AppProperties properties = new AppProperties();
        properties.setMaxRetries(3);
        properties.setInitialBackoffMs(1);
        properties.setMaxBackoffMs(5);
        BackoffExecutor executor = new BackoffExecutor(properties);

        AtomicInteger attempts = new AtomicInteger();
        InferenceClient client = item -> {
            if (attempts.incrementAndGet() < 3) {
                throw new InferenceException("rate limited", 429, true);
            }
            return "ok";
        };

        String response = executor.executeWithRetry(client, new PromptItem("1", "hello"));
        assertEquals("ok", response);
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryNonRetryableErrors() {
        AppProperties properties = new AppProperties();
        properties.setMaxRetries(5);
        properties.setInitialBackoffMs(1);
        BackoffExecutor executor = new BackoffExecutor(properties);

        InferenceClient client = item -> {
            throw new InferenceException("bad request", 400, false);
        };

        assertThrows(InferenceException.class,
                () -> executor.executeWithRetry(client, new PromptItem("1", "bad")));
    }
}

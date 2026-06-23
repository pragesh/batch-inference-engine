package com.batchinference.inference;

import com.batchinference.model.PromptItem;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process inference simulator for local development and automated tests.
 * <p>
 * Returns deterministic mock responses, injects synthetic HTTP 429 errors periodically,
 * and fails on magic prompt substrings ({@code CORRUPT_INPUT}, {@code SERVER_ERROR}).
 */
public class MockInferenceClient implements InferenceClient {

    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public String complete(PromptItem item) throws InferenceException {
        int count = callCount.incrementAndGet();
        if (count % 17 == 0) {
            throw new InferenceException("Simulated rate limit", 429, true);
        }
        if (item.prompt() != null && item.prompt().contains("CORRUPT_INPUT")) {
            throw new InferenceException("Invalid prompt payload", 400, false);
        }
        if (item.prompt() != null && item.prompt().contains("SERVER_ERROR")) {
            throw new InferenceException("Upstream server error", 500, true);
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 25));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InferenceException("Mock inference interrupted", ex, false);
        }
        return "Mock response for " + item.id() + ": processed prompt of length "
                + (item.prompt() == null ? 0 : item.prompt().length());
    }
}

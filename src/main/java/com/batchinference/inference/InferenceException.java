package com.batchinference.inference;

import com.batchinference.model.PromptItem;

/**
 * Exception raised when an inference call fails.
 * {@link #isRetryable()} indicates whether exponential backoff should be applied.
 */
public class InferenceException extends Exception {

    private final int statusCode;
    private final boolean retryable;

    public InferenceException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public InferenceException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.statusCode = 0;
        this.retryable = retryable;
    }

    /** HTTP status from the upstream API, or {@code 0} for non-HTTP failures. */
    public int getStatusCode() {
        return statusCode;
    }

    /** {@code true} for rate limits (429), server errors (5xx), and transient network faults. */
    public boolean isRetryable() {
        return retryable;
    }
}

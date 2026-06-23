package com.batchinference.inference;

import com.batchinference.model.PromptItem;

/**
 * Contract for sending a single prompt to an upstream LLM and returning generated text.
 * Implementations may call live HTTP APIs or simulate responses for testing.
 */
public interface InferenceClient {

    /**
     * Runs inference for one prompt record.
     *
     * @param item the prompt to evaluate
     * @return generated model output
     * @throws InferenceException when the call fails or the response is invalid
     */
    String complete(PromptItem item) throws InferenceException;
}

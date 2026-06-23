package com.batchinference.inference;

import com.batchinference.model.PromptItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockInferenceClientTest {

    private final MockInferenceClient client = new MockInferenceClient();

    @Test
    void returnsMockResponse() throws Exception {
        String response = client.complete(new PromptItem("1", "hello world"));
        assertTrue(response.contains("Mock response"));
    }

    @Test
    void failsOnCorruptInput() {
        assertThrows(InferenceException.class,
                () -> client.complete(new PromptItem("x", "CORRUPT_INPUT")));
    }
}

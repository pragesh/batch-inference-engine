package com.batchinference.inference;

import com.batchinference.config.InferenceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

/**
 * Inference client for DigitalOcean Gradient Serverless Inference.
 * <p>
 * Endpoint: {@code https://inference.do-ai.run/v1/chat/completions}
 */
public class DigitalOceanInferenceClient extends OpenAiCompatibleInferenceClient {

    public DigitalOceanInferenceClient(
            InferenceProperties inferenceProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        super(inferenceProperties, httpClient, objectMapper);
    }
}

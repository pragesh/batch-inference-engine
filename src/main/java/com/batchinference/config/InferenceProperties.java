package com.batchinference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for external LLM inference providers.
 * <p>
 * Supports multiple backends via {@code provider}:
 * <ul>
 *   <li>{@code mock} — in-process simulator for local development and CI</li>
 *   <li>{@code digitalocean} / {@code do} — DigitalOcean Gradient Serverless Inference</li>
 *   <li>{@code ollama} / {@code openai} — any OpenAI-compatible {@code /v1/chat/completions} API</li>
 * </ul>
 * Bound from {@code app.inference.*} in {@code application.yml} or environment variables.
 */
@ConfigurationProperties(prefix = "app.inference")
public class InferenceProperties {

    /** Provider identifier that selects the {@link com.batchinference.inference.InferenceClient} implementation. */
    private String provider = "mock";

    /** Base URL of the inference API (without trailing slash). */
    private String baseUrl = "https://inference.do-ai.run";

    /** Bearer token or model access key; required for live providers. */
    private String apiKey = "";

    /** Model identifier passed to the upstream API (e.g. {@code llama3-8b-instruct}). */
    private String model = "llama3-8b-instruct";

    /** HTTP timeout in seconds for each inference request. */
    private int timeoutSeconds = 60;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}

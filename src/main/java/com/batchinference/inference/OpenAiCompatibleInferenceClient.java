package com.batchinference.inference;

import com.batchinference.config.InferenceProperties;
import com.batchinference.model.PromptItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for OpenAI-compatible {@code /v1/chat/completions} endpoints.
 * <p>
 * Used for DigitalOcean Gradient, Ollama, Groq, Together AI, and similar providers.
 * Sends one prompt per request and extracts the assistant message content from the response.
 */
public class OpenAiCompatibleInferenceClient implements InferenceClient {

    private final InferenceProperties inferenceProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleInferenceClient(
            InferenceProperties inferenceProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.inferenceProperties = inferenceProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a single chat completion request for the given prompt item.
     *
     * @param item prompt record to evaluate
     * @return model-generated text content
     * @throws InferenceException on empty prompts, HTTP errors, or malformed responses
     */
    @Override
    public String complete(PromptItem item) throws InferenceException {
        if (item.prompt() == null || item.prompt().isBlank()) {
            throw new InferenceException("Prompt is empty", 400, false);
        }

        String baseUrl = inferenceProperties.getBaseUrl().replaceAll("/$", "");
        String url = baseUrl + "/v1/chat/completions";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", inferenceProperties.getModel());
        body.put("max_tokens", 256);
        body.put("temperature", 0.2);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", item.prompt());
        messages.add(userMessage);
        body.set("messages", messages);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(inferenceProperties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        String apiKey = inferenceProperties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            int status = response.statusCode();
            if (status == 429) {
                throw new InferenceException("Rate limited by upstream", 429, true);
            }
            if (status >= 500) {
                throw new InferenceException("Upstream server error: HTTP " + status, status, true);
            }
            if (status >= 400) {
                throw new InferenceException("Client error: HTTP " + status + " body=" + response.body(), status, false);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new InferenceException("Missing choices in response", status, false);
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new InferenceException("Empty completion content", status, false);
            }
            return content;
        } catch (InferenceException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new InferenceException("Network error calling inference API", ex, true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InferenceException("Inference call interrupted", ex, true);
        }
    }
}

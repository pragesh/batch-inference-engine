package com.batchinference.service;

import com.batchinference.dto.JobStatusResponse;
import com.batchinference.store.JobStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Sends an HTTP POST to a registered webhook URL when a batch job reaches a terminal state.
 * Failures are logged but do not affect the job outcome.
 */
@Component
public class WebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);

    private final JobStore jobStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WebhookNotifier(JobStore jobStore, ObjectMapper objectMapper) {
        this.jobStore = jobStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Fires the completion webhook asynchronously if one was registered for the job.
     */
    public void notifyCompletion(String jobId) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<String> webhookUrl = jobStore.getWebhookUrl(jobId);
                if (webhookUrl.isEmpty() || webhookUrl.get().isBlank()) {
                    return;
                }
                Optional<JobStatusResponse> status = jobStore.getJobStatus(jobId);
                if (status.isEmpty()) {
                    return;
                }

                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("jobId", jobId);
                payload.put("status", status.get().status().name());
                payload.put("total", status.get().total());
                payload.put("succeeded", status.get().succeeded());
                payload.put("failed", status.get().failed());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl.get()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("Webhook for job {} responded with HTTP {}", jobId, response.statusCode());
            } catch (Exception ex) {
                log.warn("Webhook notification failed for job {}: {}", jobId, ex.getMessage());
            }
        });
    }
}

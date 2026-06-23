package com.batchinference.integration;

import com.batchinference.dto.CreateJobRequest;
import com.batchinference.dto.JobStatusResponse;
import com.batchinference.dto.ResultItem;
import com.batchinference.model.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.worker-pool-size=1",
        "app.chunk-size=5",
        "app.inference.provider=mock"
})
class JobApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsNotFoundForUnknownJob() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/jobs/00000000-0000-0000-0000-000000000000/status",
                HttpMethod.GET,
                null,
                String.class
        );
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void returnsBadRequestWhenInputFileMissing(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        CreateJobRequest request = new CreateJobRequest(tempDir.resolve("missing.json").toString(), null);
        ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl() + "/jobs", request, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void returnsConflictWhenDownloadingBeforeJobCompletes(@org.junit.jupiter.api.io.TempDir Path tempDir)
            throws Exception {
        Path batchFile = tempDir.resolve("slow_batch.json");
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < 200; i++) {
            if (i > 0) {
                json.append(",\n");
            }
            json.append("{\"id\":\"p").append(i).append("\",\"prompt\":\"item ").append(i).append("\"}");
        }
        json.append("\n]");
        Files.writeString(batchFile, json);

        CreateJobRequest request = new CreateJobRequest(batchFile.toString(), null);
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(baseUrl() + "/jobs", request, Map.class);
        assertEquals(HttpStatus.ACCEPTED, createResponse.getStatusCode());
        String jobId = (String) createResponse.getBody().get("jobId");

        ResponseEntity<String> downloadResponse = restTemplate.exchange(
                baseUrl() + "/jobs/" + jobId + "/download",
                HttpMethod.GET,
                null,
                String.class
        );
        assertEquals(HttpStatus.CONFLICT, downloadResponse.getStatusCode());

        waitForCompletion(jobId, 120_000);
    }

    @Test
    void returnsPartialResultsWhileJobIsRunning(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        Path batchFile = tempDir.resolve("partial_batch.json");
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < 200; i++) {
            if (i > 0) {
                json.append(",\n");
            }
            json.append("{\"id\":\"p").append(i).append("\",\"prompt\":\"item ").append(i).append("\"}");
        }
        json.append("\n]");
        Files.writeString(batchFile, json);

        CreateJobRequest request = new CreateJobRequest(batchFile.toString(), null);
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(baseUrl() + "/jobs", request, Map.class);
        String jobId = (String) createResponse.getBody().get("jobId");

        List<ResultItem> partialResults = null;
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            JobStatusResponse status = restTemplate.getForObject(
                    baseUrl() + "/jobs/" + jobId + "/status",
                    JobStatusResponse.class
            );
            if (status != null && status.completed() > 0 && status.status() == JobStatus.RUNNING) {
                partialResults = restTemplate.exchange(
                        baseUrl() + "/jobs/" + jobId + "/results",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<ResultItem>>() {}
                ).getBody();
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(partialResults != null && !partialResults.isEmpty(), "Expected partial results while job running");
        assertTrue(partialResults.size() < 200, "Partial snapshot should not include all items yet");

        waitForCompletion(jobId, 120_000);
    }

    @Test
    void processesManyItemsInChunks(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        Path batchFile = tempDir.resolve("chunked_batch.json");
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < 25; i++) {
            if (i > 0) {
                json.append(",\n");
            }
            json.append("{\"id\":\"c").append(i).append("\",\"prompt\":\"chunk test ").append(i).append("\"}");
        }
        json.append("\n]");
        Files.writeString(batchFile, json);

        CreateJobRequest request = new CreateJobRequest(batchFile.toString(), null);
        ResponseEntity<Map> createResponse = restTemplate.postForEntity(baseUrl() + "/jobs", request, Map.class);
        assertEquals(HttpStatus.ACCEPTED, createResponse.getStatusCode());
        String jobId = (String) createResponse.getBody().get("jobId");

        JobStatusResponse finalStatus = waitForCompletion(jobId, 30_000);
        assertEquals(JobStatus.COMPLETED, finalStatus.status());
        assertEquals(25, finalStatus.total());
        assertEquals(25, finalStatus.succeeded());
        assertEquals(0, finalStatus.failed());
    }

    private JobStatusResponse waitForCompletion(String jobId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            JobStatusResponse status = restTemplate.getForObject(
                    baseUrl() + "/jobs/" + jobId + "/status",
                    JobStatusResponse.class
            );
            if (status != null && (status.status() == JobStatus.COMPLETED || status.status() == JobStatus.FAILED)) {
                return status;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Job did not complete within timeout");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}

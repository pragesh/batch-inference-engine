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
import org.springframework.http.HttpEntity;
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
        "app.worker-pool-size=4",
        "app.chunk-size=5",
        "app.inference.provider=mock"
})
class JobIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void processesSmallBatchEndToEnd(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        Path batchFile = tempDir.resolve("mini_batch.json");
        String json = """
                [
                  {"id":"p1","prompt":"hello"},
                  {"id":"p2","prompt":"world"},
                  {"id":"p3","prompt":"CORRUPT_INPUT"},
                  {"id":"p4","prompt":"done"}
                ]
                """;
        Files.writeString(batchFile, json);

        String baseUrl = "http://localhost:" + port;
        CreateJobRequest request = new CreateJobRequest(batchFile.toString(), null);
        ResponseEntity<Map<String, Object>> createResponse = restTemplate.exchange(
                baseUrl + "/jobs",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.ACCEPTED, createResponse.getStatusCode());
        String jobId = (String) createResponse.getBody().get("jobId");

        JobStatusResponse finalStatus = waitForCompletion(baseUrl, jobId, 30_000);
        assertEquals(4, finalStatus.total());
        assertEquals(3, finalStatus.succeeded());
        assertEquals(1, finalStatus.failed());

        List<ResultItem> results = restTemplate.exchange(
                baseUrl + "/jobs/" + jobId + "/download",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ResultItem>>() {}
        ).getBody();

        assertEquals(4, results.size());
        assertTrue(results.stream().anyMatch(item -> item.status().name().equals("FAILED")));
    }

    private JobStatusResponse waitForCompletion(String baseUrl, String jobId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            JobStatusResponse status = restTemplate.getForObject(
                    baseUrl + "/jobs/" + jobId + "/status",
                    JobStatusResponse.class
            );
            if (status != null && (status.status() == JobStatus.COMPLETED || status.status() == JobStatus.FAILED)) {
                return status;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Job did not complete within timeout");
    }
}

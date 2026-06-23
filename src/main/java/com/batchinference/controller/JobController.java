package com.batchinference.controller;

import com.batchinference.dto.CreateJobRequest;
import com.batchinference.dto.CreateJobResponse;
import com.batchinference.dto.JobStatusResponse;
import com.batchinference.dto.ResultItem;
import com.batchinference.dto.WebhookRegistrationRequest;
import com.batchinference.model.ItemStatus;
import com.batchinference.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API for asynchronous batch inference jobs.
 * <p>
 * All processing is non-blocking: {@code POST /jobs} returns a job ID immediately while
 * inference runs in the background.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** Submits a batch file for processing and returns HTTP 202 with a job ID. */
    @PostMapping
    public ResponseEntity<CreateJobResponse> createJob(@RequestBody(required = false) CreateJobRequest request) {
        CreateJobRequest effectiveRequest = request == null ? new CreateJobRequest(null, null) : request;
        CreateJobResponse response = jobService.createJob(effectiveRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /** Returns job status and aggregate success/failure counts. */
    @GetMapping("/{jobId}/status")
    public JobStatusResponse getStatus(@PathVariable String jobId) {
        return jobService.getStatus(jobId);
    }

    /**
     * Returns completed item results at any time — including while the job is still running.
     * Use this for partial snapshots (e.g. 68 of 1000 succeeded so far).
     */
    @GetMapping("/{jobId}/results")
    public List<ResultItem> results(
            @PathVariable String jobId,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return jobService.getResults(jobId, status, limit, offset);
    }

    /** Returns the full result array once the job has finished (HTTP 409 while running). */
    @GetMapping("/{jobId}/download")
    public List<ResultItem> download(@PathVariable String jobId) {
        return jobService.downloadResults(jobId);
    }

    /** Registers a URL to receive an HTTP callback when the job completes. */
    @PostMapping("/{jobId}/webhook")
    public Map<String, String> registerWebhook(
            @PathVariable String jobId,
            @Valid @RequestBody WebhookRegistrationRequest request
    ) {
        jobService.registerWebhook(jobId, request.url());
        return Map.of("message", "Webhook registered", "jobId", jobId);
    }
}

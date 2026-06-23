package com.batchinference.service;

import com.batchinference.config.AppProperties;
import com.batchinference.dto.CreateJobRequest;
import com.batchinference.dto.CreateJobResponse;
import com.batchinference.dto.JobStatusResponse;
import com.batchinference.dto.ResultItem;
import com.batchinference.model.ItemStatus;
import com.batchinference.model.JobStatus;
import com.batchinference.store.JobStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Application service for batch job lifecycle: create, poll status, download results,
 * and register completion webhooks.
 */
@Service
public class JobService {

    private final AppProperties appProperties;
    private final JobStore jobStore;
    private final BatchProcessor batchProcessor;

    public JobService(AppProperties appProperties, JobStore jobStore, BatchProcessor batchProcessor) {
        this.appProperties = appProperties;
        this.jobStore = jobStore;
        this.batchProcessor = batchProcessor;
    }

    /**
     * Accepts a new batch job and dispatches background processing.
     *
     * @param request optional input file path and webhook URL
     * @return job identifier and initial pending status
     */
    public CreateJobResponse createJob(CreateJobRequest request) {
        String fileName = request.inputFile() == null || request.inputFile().isBlank()
                ? appProperties.getDefaultBatchFile()
                : request.inputFile();
        Path inputFile = resolveInputFile(fileName);

        try {
            String jobId = jobStore.createJob(inputFile.toString(), request.webhookUrl());
            batchProcessor.processAsync(jobId, inputFile);
            return new CreateJobResponse(
                    jobId,
                    JobStatus.PENDING,
                    Instant.now(),
                    "Job accepted and processing in background"
            );
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create job", ex);
        }
    }

    /** Returns aggregate progress counters for a job. */
    public JobStatusResponse getStatus(String jobId) {
        try {
            return jobStore.getJobStatus(jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read job status", ex);
        }
    }

    /**
     * Returns completed item results while a job is still running (partial snapshot)
     * or after it finishes. Pending items are excluded unless {@code status} is set.
     */
    public List<ResultItem> getResults(String jobId, ItemStatus status, Integer limit, Integer offset) {
        getStatus(jobId);
        try {
            return jobStore.queryResults(jobId, status, limit, offset);
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read job results", ex);
        }
    }

    /**
     * Returns the compiled per-row result array once the job has finished.
     *
     * @throws ResponseStatusException HTTP 409 if the job is still running
     */
    public List<ResultItem> downloadResults(String jobId) {
        JobStatusResponse status = getStatus(jobId);
        if (status.status() == JobStatus.PENDING || status.status() == JobStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Job is not complete yet");
        }
        try {
            return jobStore.getResults(jobId);
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read job results", ex);
        }
    }

    /** Associates a callback URL to be notified when the job completes or fails. */
    public void registerWebhook(String jobId, String webhookUrl) {
        getStatus(jobId);
        try {
            jobStore.updateWebhookUrl(jobId, webhookUrl);
        } catch (SQLException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to register webhook", ex);
        }
    }

    private Path resolveInputFile(String fileName) {
        Path candidate = Path.of(fileName);
        if (!candidate.isAbsolute()) {
            candidate = Path.of(appProperties.getDataDir()).resolve(fileName);
        }
        if (!Files.exists(candidate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Input file not found: " + candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }
}

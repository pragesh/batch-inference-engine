package com.batchinference.service;

import com.batchinference.config.AppProperties;
import com.batchinference.config.SpacesProperties;
import com.batchinference.dto.ResultItem;
import com.batchinference.inference.InferenceClient;
import com.batchinference.inference.InferenceException;
import com.batchinference.model.JobStatus;
import com.batchinference.model.PromptItem;
import com.batchinference.retry.BackoffExecutor;
import com.batchinference.spaces.SpacesCheckpointWriter;
import com.batchinference.store.JobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background scatter-gather engine for batch inference jobs.
 * <p>
 * Reads prompts in fixed-size chunks, dispatches each item to a bounded worker pool,
 * applies retry/backoff on upstream rate limits, persists per-row results, and optionally
 * streams checkpoints to DigitalOcean Spaces.
 */
@Service
public class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);

    private final AppProperties appProperties;
    private final SpacesProperties spacesProperties;
    private final JobStore jobStore;
    private final BatchFileReader batchFileReader;
    private final InferenceClient inferenceClient;
    private final BackoffExecutor backoffExecutor;
    private final ExecutorService workerExecutor;
    private final ExecutorService jobDispatchExecutor;
    private final SpacesCheckpointWriter spacesCheckpointWriter;
    private final WebhookNotifier webhookNotifier;

    public BatchProcessor(
            AppProperties appProperties,
            SpacesProperties spacesProperties,
            JobStore jobStore,
            BatchFileReader batchFileReader,
            InferenceClient inferenceClient,
            BackoffExecutor backoffExecutor,
            ExecutorService workerExecutor,
            ExecutorService jobDispatchExecutor,
            SpacesCheckpointWriter spacesCheckpointWriter,
            WebhookNotifier webhookNotifier
    ) {
        this.appProperties = appProperties;
        this.spacesProperties = spacesProperties;
        this.jobStore = jobStore;
        this.batchFileReader = batchFileReader;
        this.inferenceClient = inferenceClient;
        this.backoffExecutor = backoffExecutor;
        this.workerExecutor = workerExecutor;
        this.jobDispatchExecutor = jobDispatchExecutor;
        this.spacesCheckpointWriter = spacesCheckpointWriter;
        this.webhookNotifier = webhookNotifier;
    }

    /**
     * Starts processing on a background thread and returns immediately to the caller.
     *
     * @param jobId     identifier of the job to process
     * @param inputFile path to the JSON prompt array file
     */
    public void processAsync(String jobId, Path inputFile) {
        CompletableFuture.runAsync(() -> processJob(jobId, inputFile), jobDispatchExecutor);
    }

    private void processJob(String jobId, Path inputFile) {
        try {
            jobStore.updateJobStatus(jobId, JobStatus.RUNNING, null);
            Semaphore concurrency = new Semaphore(appProperties.getWorkerPoolSize());
            AtomicInteger blockNumber = new AtomicInteger();

            batchFileReader.streamChunks(inputFile, appProperties.getChunkSize(), chunk -> {
                try {
                    jobStore.insertItems(jobId, chunk);
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (PromptItem item : chunk) {
                        futures.add(CompletableFuture.runAsync(() -> {
                            try {
                                concurrency.acquire();
                                processItem(jobId, item);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                try {
                                    jobStore.markItemFailed(jobId, item.id(), "Processing interrupted");
                                } catch (SQLException sqlEx) {
                                    log.error("Failed to mark item interrupted {}", item.id(), sqlEx);
                                }
                            } finally {
                                concurrency.release();
                            }
                        }, workerExecutor));
                    }
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

                    if (spacesProperties.isEnabled()) {
                        int currentBlock = blockNumber.incrementAndGet();
                        List<ResultItem> snapshot = jobStore.getResults(jobId).stream()
                                .filter(item -> chunk.stream().anyMatch(c -> c.id().equals(item.id())))
                                .toList();
                        spacesCheckpointWriter.writeCheckpoint(jobId, currentBlock, snapshot);
                    }
                } catch (SQLException ex) {
                    throw new RuntimeException("Database error while processing chunk", ex);
                }
            });

            jobStore.updateJobStatus(jobId, JobStatus.COMPLETED, null);
            webhookNotifier.notifyCompletion(jobId);
        } catch (Exception ex) {
            log.error("Job {} failed", jobId, ex);
            try {
                jobStore.updateJobStatus(jobId, JobStatus.FAILED, ex.getMessage());
            } catch (SQLException sqlEx) {
                log.error("Failed to update failed status for job {}", jobId, sqlEx);
            }
            webhookNotifier.notifyCompletion(jobId);
        }
    }

    private void processItem(String jobId, PromptItem item) {
        try {
            String response = backoffExecutor.executeWithRetry(inferenceClient, item);
            jobStore.markItemSuccess(jobId, item.id(), response);
        } catch (InferenceException ex) {
            log.warn("Inference failed for item {}: {}", item.id(), ex.getMessage());
            try {
                jobStore.markItemFailed(jobId, item.id(), ex.getMessage());
            } catch (SQLException sqlEx) {
                log.error("Failed to persist item failure for {}", item.id(), sqlEx);
            }
        } catch (SQLException sqlEx) {
            log.error("Failed to persist item success for {}", item.id(), sqlEx);
        }
    }
}

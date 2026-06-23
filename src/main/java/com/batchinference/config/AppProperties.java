package com.batchinference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Core application settings for batch job orchestration.
 * <p>
 * Covers data paths, worker-pool sizing, chunking, and retry/backoff tuning.
 * Inference and Spaces settings live in dedicated configuration classes.
 * Bound from {@code app.*} in {@code application.yml} or environment variables.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Directory for input batch files and the SQLite job database. */
    private String dataDir = "./data";

    /** Default input filename when {@code POST /jobs} omits {@code inputFile}. */
    private String defaultBatchFile = "sample_batch.json";

    /** Maximum concurrent inference calls across the worker pool. */
    private int workerPoolSize = 10;

    /** Number of prompt records read and dispatched per scatter wave. */
    private int chunkSize = 50;

    /** Maximum retry attempts per prompt for retryable upstream errors (429, 5xx). */
    private int maxRetries = 5;

    /** Initial backoff delay in milliseconds before the first retry. */
    private long initialBackoffMs = 500;

    /** Upper bound for exponential backoff delay in milliseconds. */
    private long maxBackoffMs = 30_000;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getDefaultBatchFile() {
        return defaultBatchFile;
    }

    public void setDefaultBatchFile(String defaultBatchFile) {
        this.defaultBatchFile = defaultBatchFile;
    }

    public int getWorkerPoolSize() {
        return workerPoolSize;
    }

    public void setWorkerPoolSize(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public void setInitialBackoffMs(long initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }
}

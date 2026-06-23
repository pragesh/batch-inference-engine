package com.batchinference.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for DigitalOcean Spaces checkpoint streaming.
 * <p>
 * When enabled, completed chunk results are uploaded as JSON blocks to an S3-compatible
 * bucket so partial progress survives container crashes on long-running batch jobs.
 * Bound from {@code app.spaces.*} in {@code application.yml} or environment variables.
 */
@ConfigurationProperties(prefix = "app.spaces")
public class SpacesProperties {

    /** Whether to upload intermediate checkpoints after each processed chunk. */
    private boolean enabled;

    /** S3-compatible endpoint (e.g. {@code https://nyc3.digitaloceanspaces.com}). */
    private String endpoint = "";

    /** Spaces region identifier. */
    private String region = "nyc3";

    /** Target bucket name. */
    private String bucket = "";

    /** Spaces access key. */
    private String accessKey = "";

    /** Spaces secret key. */
    private String secretKey = "";

    /** Number of completed items between optional checkpoint triggers (reserved for future use). */
    private int checkpointInterval = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getCheckpointInterval() {
        return checkpointInterval;
    }

    public void setCheckpointInterval(int checkpointInterval) {
        this.checkpointInterval = checkpointInterval;
    }
}

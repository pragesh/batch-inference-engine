package com.batchinference.spaces;

import com.batchinference.config.SpacesProperties;
import com.batchinference.dto.ResultItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.List;

/**
 * Uploads intermediate batch results to DigitalOcean Spaces (S3-compatible storage).
 * <p>
 * Each checkpoint is stored at {@code jobs/{jobId}/blocks/block-{n}.json} so large jobs
 * retain partial progress if the processing container is restarted unexpectedly.
 */
@Component
public class SpacesCheckpointWriter {

    private static final Logger log = LoggerFactory.getLogger(SpacesCheckpointWriter.class);

    private final SpacesProperties spacesProperties;
    private final ObjectMapper objectMapper;
    private S3Client s3Client;

    public SpacesCheckpointWriter(SpacesProperties spacesProperties, ObjectMapper objectMapper) {
        this.spacesProperties = spacesProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!spacesProperties.isEnabled()) {
            return;
        }
        s3Client = S3Client.builder()
                .region(Region.of(spacesProperties.getRegion()))
                .endpointOverride(URI.create(spacesProperties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(spacesProperties.getAccessKey(), spacesProperties.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    /**
     * Writes a JSON snapshot of completed items for one processing block.
     *
     * @param jobId       batch job identifier
     * @param blockNumber monotonically increasing block index within the job
     * @param items       result rows to persist
     */
    public void writeCheckpoint(String jobId, int blockNumber, List<ResultItem> items) {
        if (!spacesProperties.isEnabled() || s3Client == null || items.isEmpty()) {
            return;
        }
        try {
            String key = "jobs/" + jobId + "/blocks/block-" + blockNumber + ".json";
            byte[] payload = objectMapper.writeValueAsBytes(items);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(spacesProperties.getBucket())
                    .key(key)
                    .contentType("application/json")
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(payload));
            log.info("Wrote checkpoint {} for job {}", key, jobId);
        } catch (Exception ex) {
            log.warn("Failed to write Spaces checkpoint for job {} block {}: {}", jobId, blockNumber, ex.getMessage());
        }
    }
}

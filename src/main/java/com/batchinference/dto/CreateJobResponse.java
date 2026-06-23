package com.batchinference.dto;

import com.batchinference.model.JobStatus;

import java.time.Instant;

public record CreateJobResponse(
        String jobId,
        JobStatus status,
        Instant createdAt,
        String message
) {
}

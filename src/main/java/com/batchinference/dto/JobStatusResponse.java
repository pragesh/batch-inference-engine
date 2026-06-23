package com.batchinference.dto;

import com.batchinference.model.JobStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobStatusResponse(
        String jobId,
        JobStatus status,
        int total,
        int completed,
        int succeeded,
        int failed,
        int pending,
        Instant createdAt,
        Instant updatedAt,
        String sourceFile,
        String errorMessage
) {
}

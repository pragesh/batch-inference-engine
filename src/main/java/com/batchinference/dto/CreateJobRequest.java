package com.batchinference.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateJobRequest(
        String inputFile,
        String webhookUrl
) {
    public CreateJobRequest {
        if (webhookUrl != null && webhookUrl.isBlank()) {
            webhookUrl = null;
        }
    }
}

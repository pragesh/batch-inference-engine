package com.batchinference.dto;

import jakarta.validation.constraints.NotBlank;

public record WebhookRegistrationRequest(
        @NotBlank String url
) {
}

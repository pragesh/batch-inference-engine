package com.batchinference.dto;

import com.batchinference.model.ItemStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultItem(
        String id,
        ItemStatus status,
        String prompt,
        String response,
        String error
) {
}

package com.batchinference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Batch Inference Engine — an asynchronous scatter-gather service
 * that evaluates large prompt batches against external LLM inference endpoints.
 */
@SpringBootApplication
@EnableAsync
public class BatchInferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchInferenceApplication.class, args);
    }
}

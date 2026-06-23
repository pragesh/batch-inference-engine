package com.batchinference.config;

import com.batchinference.inference.DigitalOceanInferenceClient;
import com.batchinference.inference.InferenceClient;
import com.batchinference.inference.MockInferenceClient;
import com.batchinference.inference.OpenAiCompatibleInferenceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring configuration for shared beans: JSON serialization, HTTP client,
 * worker executors, and the pluggable {@link InferenceClient}.
 */
@Configuration
@EnableConfigurationProperties({AppProperties.class, InferenceProperties.class, SpacesProperties.class})
public class AppConfig {

    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    HttpClient httpClient(InferenceProperties inferenceProperties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(inferenceProperties.getTimeoutSeconds()))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService workerExecutor(AppProperties appProperties) {
        int poolSize = appProperties.getWorkerPoolSize();
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("inference-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(poolSize, factory);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService jobDispatchExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("job-dispatch");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Selects the inference backend from {@link InferenceProperties#getProvider()}.
     */
    @Bean
    InferenceClient inferenceClient(
            InferenceProperties inferenceProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        String provider = inferenceProperties.getProvider().toLowerCase();
        return switch (provider) {
            case "digitalocean", "do" -> new DigitalOceanInferenceClient(inferenceProperties, httpClient, objectMapper);
            case "ollama", "openai" -> new OpenAiCompatibleInferenceClient(inferenceProperties, httpClient, objectMapper);
            default -> new MockInferenceClient();
        };
    }
}

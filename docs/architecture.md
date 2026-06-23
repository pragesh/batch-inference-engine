# Architecture

## Overview

The Batch Inference Engine is an async scatter-gather pipeline:

1. **Ingest** — REST API accepts a job and returns a UUID immediately.
2. **Scatter** — A background dispatcher streams the input JSON array in fixed-size chunks.
3. **Execute** — Each prompt is processed by a bounded worker pool calling an external inference API.
4. **Backpressure** — Workers retry 429/5xx responses with exponential backoff and jitter.
5. **Gather** — Per-row results are persisted to SQLite and exposed via status/download endpoints.

## Components

### REST API (`JobController`)

- Non-blocking job submission (`HTTP 202 Accepted`).
- Status polling and final download.
- Optional webhook registration.

### Batch File Reader

Uses Jackson streaming (`JsonParser`) to iterate a top-level JSON array without deserializing the full file. This is the primary safeguard against OOM when scaling from 1,000 to 500,000 items.

### Batch Processor

- Runs on a dedicated `jobDispatchExecutor` thread (avoids starving the worker pool).
- Inserts each chunk into SQLite, then fans out item tasks.
- `Semaphore` enforces max in-flight inference calls equal to `WORKER_POOL_SIZE`.

### Inference Clients

| Client | When |
|--------|------|
| `MockInferenceClient` | Local dev, CI — simulates 429s |
| `DigitalOceanInferenceClient` | Production on DO Gradient |
| `OpenAiCompatibleInferenceClient` | Ollama, Groq, Together, etc. |

### Job Store (SQLite)

Tables:

- `jobs` — metadata, aggregate counters, webhook URL
- `job_items` — one row per prompt with status/response/error

Counters are updated incrementally so status polling is cheap.

### Spaces Checkpoint Writer

Optional extension: after each chunk completes, uploads a JSON block to DigitalOcean Spaces at:

```
s3://{bucket}/jobs/{jobId}/blocks/block-{n}.json
```

Enables recovery of partial progress if the container crashes mid-job.

### Webhook Notifier

Fires an async HTTP POST when a job reaches `COMPLETED` or `FAILED`.

## Scaling Thresholds

| Scale | Bottleneck | Mitigation |
|-------|------------|------------|
| 1K prompts | None on single node | Default config |
| 100K prompts | SQLite write throughput | Increase `CHUNK_SIZE`, tune pool |
| 500K prompts | Disk I/O, job duration | Spaces checkpoints, larger volume |
| Multi-instance | SQLite not shared | PostgreSQL + Redis queue |
| High 429 rate | Upstream limits | Lower `WORKER_POOL_SIZE`, longer backoff |

## DigitalOcean Topology

```
Internet
   │
   ▼
DO App Platform (container)
   ├── Spring Boot API :8080
   ├── SQLite on persistent volume
   └── Env: INFERENCE_API_KEY
           │
           ▼
DO Gradient Serverless Inference
   https://inference.do-ai.run

Optional:
   DO Spaces ← checkpoint blocks
```

## Failure Model

| Failure | Behavior |
|---------|----------|
| HTTP 429 | Retry with backoff + jitter |
| HTTP 5xx | Retry up to `MAX_RETRIES` |
| HTTP 4xx (bad prompt) | Mark row `FAILED`, continue job |
| Corrupt input row | Parse error fails chunk/job |
| Worker crash | Job may stall; Spaces checkpoints aid recovery |

#!/usr/bin/env python3
"""Generate sample_batch.json with 1,000 prompt items."""
import json
from pathlib import Path

PROMPTS = [
    "Explain recursion in one sentence.",
    "List three benefits of unit testing.",
    "What is HTTP 429?",
    "Summarize the CAP theorem.",
    "Write a haiku about distributed systems.",
    "Define idempotency in APIs.",
    "What is exponential backoff?",
    "Describe a bounded worker pool.",
    "When should you use async job queues?",
    "What is scatter-gather processing?",
]

def main() -> None:
    data_dir = Path(__file__).resolve().parent.parent / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    items = []
    for i in range(1, 1001):
        prompt = PROMPTS[i % len(PROMPTS)] + f" (item {i})"
        if i == 500:
            prompt = "CORRUPT_INPUT trigger failure"
        items.append({"id": f"prompt-{i:04d}", "prompt": prompt})
    output = data_dir / "sample_batch.json"
    with output.open("w", encoding="utf-8") as f:
        json.dump(items, f, indent=2)
    print(f"Wrote {len(items)} items to {output}")

if __name__ == "__main__":
    main()

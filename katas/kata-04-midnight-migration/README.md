# Kata 4 — The Midnight Migration

**Difficulty:** 4/5

## Context

A `@Scheduled` reconciliation job runs nightly. It processes thousands of records, takes 2-3 hours, and has no visibility into progress. When it fails (weekly), the team manually figures out which records were processed and which were not.

## Challenge

Migrate the job to a Temporal cron workflow that:

1. Runs **daily at midnight UTC** (cron `0 0 * * *`)
2. Fetches unreconciled records in batches of **100**
3. Processes each record as an individual activity
4. Uses **`Continue-As-New`** when the event history exceeds **5,000 events**
5. Reports progress via `@QueryMethod`
6. Sends a summary notification when complete

## Constraints

- Workflow must be idempotent — safe to restart
- Each record processing is independently retryable
- Failed records logged but NOT blocking the overall process
- Fixed workflow id (`nightly-reconciliation`) to prevent duplicate runs

## Try it

Start the app — the cron workflow gets scheduled automatically on boot (see `TemporalConfig#start`):

```bash
mvn spring-boot:run
# Then check progress:
curl localhost:8084/api/reconciliation/progress
```

Open the Temporal UI at <http://localhost:8233> and find `nightly-reconciliation` — you'll see it scheduled with the cron expression.

For development you can force an immediate run by changing the cron to `* * * * *` (every minute) in `TemporalConfig#start`.

## Solution outline

1. Loop: `fetchBatch(offset, 100)` until empty.
2. For each record, try `processRecord`; on exception, log + increment `failed`, continue.
3. After each batch, check `Workflow.getInfo().getHistoryLength()`; if > 5000, call `Workflow.continueAsNew(currentOffset)`.
4. After the loop, call `sendSummary(processed, failed)`.
5. The cron schedule + workflow id are already wired in `TemporalConfig`.

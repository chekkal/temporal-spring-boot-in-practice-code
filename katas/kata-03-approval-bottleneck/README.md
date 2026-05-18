# Kata 3 — The Approval Bottleneck

**Difficulty:** 3/5

## Context

Orders above $5,000 require manager approval. The current system sends an email and polls a database table every 60 seconds. Approvals take anywhere from minutes to days, and the polling creates unnecessary database load.

## Challenge

Implement `HighValueOrderWorkflow` that:

1. Checks if order total exceeds $5,000
2. If yes, sends an approval request and waits for a signal
3. Times out after 48 hours (with **escalation at 24h**)
4. If approved → continue with fulfillment
5. If rejected → notify customer and end
6. Exposes `@QueryMethod` for approval status

## Constraints

- The wait must NOT consume threads (use `Workflow.await`)
- Approval signal delivered via REST
- Workflow must survive worker restarts during the wait

## Try it

```bash
# Start a workflow needing approval
curl -X POST 'localhost:8083/api/orders/ord-big?total=10000'

# Approve it
curl -X POST 'localhost:8083/api/orders/ord-big/approve?approver=manager-1&comment=ok'

# Or reject:
curl -X POST 'localhost:8083/api/orders/ord-big/reject?approver=manager-1&reason=too+pricey'

# Auto-approved (under threshold):
curl -X POST 'localhost:8083/api/orders/ord-small?total=99'
```

## Solution outline

1. Define workflow with `@WorkflowMethod`, two `@SignalMethod`s, one `@QueryMethod`.
2. In the body: `Workflow.await(Duration.ofHours(24), () -> decision != null)`. If returns false, escalate, wait another 24h.
3. If still no decision after 48h total: auto-reject.
4. Open `HighValueOrderWorkflowImpl.java` — TODO body marks where to write the logic.

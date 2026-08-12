# Use Cases — which Temporal pattern solves which problem

Start here if you're evaluating Temporal and want to know whether it fits a problem you actually have. Each entry states the problem in production terms, why durable execution helps, and points at runnable code in this repository.

If you already know you want Temporal and just want to write some, skip to the [katas](../katas/).

---

## 1. Distributed transactions across services (the saga pattern)

**The problem.** An order touches payment, inventory, shipping, and notifications. Step three fails. Payment is already captured and stock is already reserved, and there is no database transaction spanning four services to roll any of it back. You end up writing compensation logic by hand, in every service, and getting it subtly wrong.

**Why Temporal.** The workflow *is* the transaction boundary. Compensation is ordinary code in a `catch` block, executed in reverse order, and it survives the process dying halfway through the rollback.

**Code:** [`order-platform/`](../order-platform/) — full reference service
[`OrderFulfillmentWorkflowImpl.java`](../order-platform/order-service/src/main/java/com/example/order/workflow/OrderFulfillmentWorkflowImpl.java)
**Practise:** [`kata-01-lost-order`](../katas/kata-01-lost-order/) (★★☆☆☆) · [`kata-02-compensation-dance`](../katas/kata-02-compensation-dance/) (★★★☆☆)

---

## 2. Workflows that wait for a human

**The problem.** A refund over €500 needs a manager's approval. The process might wait four days. A thread cannot wait four days, so you shred the process into a state machine, a database table, a polling job, and a reminder email — four moving parts to express "wait for a person."

**Why Temporal.** `Workflow.await()` blocks for as long as it takes — minutes or months — without holding a thread or a connection. The waiting is free, the state is durable, and escalation is a timer next to the await.

**Code:** [`OrderApprovalWorkflowImpl.java`](../order-platform/order-service/src/main/java/com/example/order/workflow/OrderApprovalWorkflowImpl.java)
**Practise:** [`kata-03-approval-bottleneck`](../katas/kata-03-approval-bottleneck/) (★★★☆☆)

---

## 3. Scheduled jobs that must not be missed

**The problem.** `@Scheduled` runs on whichever instance wakes first, silently does nothing if that instance is down, gives you no history of what ran, and quietly double-runs when you scale to two replicas. Nobody notices until the month-end billing job skips a night.

**Why Temporal.** A cron workflow has an execution history you can inspect, exactly-once semantics across replicas, and Continue-As-New to run indefinitely without unbounded history growth.

**Practise:** [`kata-04-midnight-migration`](../katas/kata-04-midnight-migration/) (★★★★☆)

---

## 4. Third-party APIs that fail

**The problem.** The payment provider times out intermittently. You add a retry, then a backoff, then a circuit breaker, then a fallback provider — and now the retry logic is more complex than the business logic, and untestable.

**Why Temporal.** Retry policy is declarative configuration on the activity: attempts, backoff coefficient, non-retryable error types. The workflow code stays a straight line and reads as business logic again.

**Code:** `ActivityOptions` in [`OrderFulfillmentWorkflowImpl.java`](../order-platform/order-service/src/main/java/com/example/order/workflow/OrderFulfillmentWorkflowImpl.java)
**Practise:** [`kata-05-cascading-failure`](../katas/kata-05-cascading-failure/) (★★★★☆)

---

## 5. Changing a workflow that is already running

**The problem.** You need to add a fraud check to the order flow. Eight thousand orders are mid-flight, some of them days old. Deploy the change and those in-flight executions hit a history that no longer matches their code.

**Why Temporal.** `Workflow.getVersion()` lets old executions finish on the old path while new ones take the new one, from a single codebase, with no maintenance branch.

**Practise:** [`kata-06-schema-evolution`](../katas/kata-06-schema-evolution/) (★★★★★)

---

## 6. Multi-step AI agents

**The problem.** An agent plans, searches, calls a model six times, waits for a human to approve a spend, then writes a report. The pod restarts at step seven. Without durable state you start again at step one — and you pay for all six model calls a second time.

**Why Temporal.** Each model call is an activity, so its result is recorded in history. A restart replays from history rather than re-invoking the model: you resume at step seven and pay nothing to get there. Rate limits become a retry policy, and the human approval gate is the same `Workflow.await()` as use case 2.

**Code:** [`agentic-coordination/`](agentic-coordination/) — a research agent with a budget gate, runnable with **no API key** (the provider is simulated). Kill it mid-run and restart to watch it resume without re-billing.
**Tests:** `mvn test` — five tests, no server and no Docker required.

---

## Not sure whether you need Temporal at all?

You probably don't, if your process is a single request/response under a second, is idempotent to retry as a whole, and has nothing to unwind on failure. A retry annotation is cheaper than a workflow engine.

You probably do, if any of these are true:

- the process spans more than one service and partial failure leaves inconsistent state
- it waits for something slow — a person, a batch, an external system
- someone has asked "what happened to order 47?" and the answer required reading logs
- you have written the words `status`, `retry_count`, and `last_attempted_at` into a database table

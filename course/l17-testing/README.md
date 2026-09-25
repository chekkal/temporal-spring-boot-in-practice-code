# Lecture 17 — Testing Workflows with JUnit and TestWorkflowEnvironment

Code for Lecture 17 of the course. The production code is small on purpose: an `OrderWorkflow` (charge → reserve stock → ship) and an `ApprovalWorkflow` (wait up to 72 hours for a signal). The tests are the point. There is one test class per technique on the slides, and none of them needs a Temporal server.

Package `com.example.course.l17`, port `8096`, task queue `course-l17`.

## Slide to code

| Slide | Technique | Test class / test |
|-------|-----------|-------------------|
| 2 — TestWorkflowEnvironment Setup | `@RegisterExtension TestWorkflowExtension` with workflow types and activity impls | `OrderWorkflowHappyPathTest` (the `testWorkflow` field) |
| 3 — Happy Path Test | `processOrder(new OrderRequest("item-1", 2, "card-123"))` → `COMPLETED`, `PAY-...` | `OrderWorkflowHappyPathTest.orderWorkflow_completesSuccessfully` |
| 4 — Testing Compensation with Mocks | inventory mock throws, payment mock returns `PAY-001`, `verify(paymentMock).refund("PAY-001")`, `TestWorkflowEnvironment.newInstance()` by hand | `OrderWorkflowCompensationTest.orderWorkflow_refundsOnInventoryFailure` |
| 5 — Mocked Activities for Fast Isolation | all activities mocked, digital product, `verify(shipping, never()).createShipment(any())` | `OrderWorkflowMockedActivitiesTest.workflow_skipsShippingForDigitalProducts` |
| 6 — Testing Signals | `WorkflowClient.start`, `approve("manager-1", ...)`, `getResult(5, SECONDS)` → `APPROVED` | `ApprovalWorkflowSignalTest.approvalWorkflow_completesAfterSignal` |
| 7 — Time-Skipping for Timeout Tests | `testEnv.sleep(Duration.ofHours(73))` → `TIMED_OUT` | `ApprovalWorkflowTimeSkippingTest.orderWorkflow_timesOutAfter72Hours` |
| 8 — Combining with Spring Boot Test | `@SpringBootTest`, `@ActiveProfiles("test")`, mocked `PaymentGateway` / `InventoryService`, `@Autowired WorkflowClient` | `OrderWorkflowIntegrationTest.fullOrderFlow_withSpringContext` with `TemporalTestConfig` |
| — (bonus, leads into lecture 19) | replaying a recorded history with `WorkflowReplayer` | `OrderWorkflowReplayTest` |

Production code the tests exercise:

| Class | What it does |
|-------|--------------|
| `OrderWorkflowImpl` | `charge` → `reserveStock` → `createShipment` (skipped when `digital` is true). `reserveStock` returning `false` or failing → `refund`, status `REFUNDED`. A failed shipment also refunds. A declined card → `PAYMENT_FAILED`. Logs with `Workflow.getLogger`. Activities retry at most 3 times. |
| `ApprovalWorkflowImpl` | `Workflow.await(72h, ...)` for an `approve` or `reject` signal. Returns `APPROVED`, `REJECTED` or `TIMED_OUT`. |
| `PaymentActivityImpl` | calls `PaymentGateway.charge(token)` → `ChargeResult`, returns `PaymentResult(paymentId)`. A declined card becomes a non-retryable `PaymentDeclined` failure. |
| `InventoryActivityImpl` | calls `InventoryService.reserve(itemId, quantity)`. |
| `ShippingActivityImpl` | returns `SHIP-<uuid>`. |
| `InMemoryPaymentGateway`, `InMemoryInventoryService` | the in-memory fakes behind the `PaymentGateway` and `InventoryService` interfaces. These two interfaces are what slide 8 mocks. |
| `TemporalConfig` | real Temporal connection, `@Profile("!test")` |
| `OrderController` | REST endpoints for running the workflows by hand |

Failure triggers in the fakes:

| Trigger | Where | Result |
|---------|-------|--------|
| `paymentToken` contains `decline` | `InMemoryPaymentGateway` | `PAYMENT_FAILED`, nothing to refund |
| `itemId` starts with `oos-` | `InMemoryInventoryService` returns `false` | `REFUNDED` |
| `itemId` starts with `error-` | `InMemoryInventoryService` throws | 3 attempts (about 3 s of back-off on a real server), then `REFUNDED` |

## Run the tests

```bash
# from the repo root
mvn -B -f course/l17-testing/pom.xml verify

# one class
mvn -B -f course/l17-testing/pom.xml test -Dtest=ApprovalWorkflowTimeSkippingTest
```

15 tests in 7 classes. `ApprovalWorkflowTimeSkippingTest` prints how much real time the 73 hours took (tens of milliseconds on a laptop).

What each class adds on top of the slide:

- `OrderWorkflowHappyPathTest` also runs the `oos-` and `decline` paths through the real activity implementations.
- `OrderWorkflowCompensationTest` has a second test where `reserveStock` returns `false` instead of throwing. Both lead to the refund.
- `OrderWorkflowMockedActivitiesTest` uses `TestWorkflowExtension` with `setDoNotStart(true)`, so each test registers fresh mocks on the injected `Worker` and then calls `testEnv.start()`. A second test checks the call order with `InOrder`.
- `ApprovalWorkflowSignalTest` also covers the `reject` signal.
- `ApprovalWorkflowTimeSkippingTest` also approves at hour 71 to show the deadline is not hit early.
- `OrderWorkflowIntegrationTest` loads the full Spring context under the `test` profile. `TemporalConfig` is `@Profile("!test")`, so it is skipped. `TemporalTestConfig` (imported with `@Import`) creates a `TestWorkflowEnvironment`, exposes its `WorkflowClient` as the bean, and registers a worker with the real `@Component` activity beans. Those beans were built with the `@MockitoBean` gateway and inventory service, so the test runs Spring wiring + activities + workflow without a Temporal server. A second test checks that an out-of-stock reply leads to `paymentGateway.refund("PAY-002")`.
- `OrderWorkflowReplayTest` runs an order, fetches its history with `client.fetchHistory(...)`, writes it to `target/order-workflow-history.json`, and replays it with `WorkflowReplayer` against `OrderWorkflowImpl` (passes). It then replays the same history against `ReorderedOrderWorkflowImpl`, which reserves stock before charging, and expects a `NonDeterministicException`. That is what would happen to orders already in flight if you deployed such a change. Lecture 19 covers how to make that kind of change safely.

## Run the app

The tests are the main content, but the same workflows can be run against a real server. Start Temporal once (from the repo root):

```bash
cd order-platform/docker && docker compose up -d
# Temporal UI: http://localhost:8233
```

Then:

```bash
mvn -f course/l17-testing/pom.xml spring-boot:run
```

### Orders

`POST /api/orders` runs `processOrder` and waits for the result.

```bash
# physical product: charged, reserved, shipped
curl -X POST localhost:8096/api/orders -H 'Content-Type: application/json' \
  -d '{"itemId":"item-1","quantity":2,"paymentToken":"card-123"}'
# {"status":"COMPLETED","paymentId":"PAY-...","shipmentId":"SHIP-..."}

# digital product: no shipment
curl -X POST localhost:8096/api/orders -H 'Content-Type: application/json' \
  -d '{"itemId":"ebook-1","quantity":1,"paymentToken":"card-123","digital":true}'
# {"status":"COMPLETED","paymentId":"PAY-...","shipmentId":null}

# out of stock: refunded
curl -X POST localhost:8096/api/orders -H 'Content-Type: application/json' \
  -d '{"itemId":"oos-item","quantity":1,"paymentToken":"card-123"}'
# {"status":"REFUNDED",...}

# inventory system down: 3 attempts, then refunded (watch the retries in the UI)
curl -X POST localhost:8096/api/orders -H 'Content-Type: application/json' \
  -d '{"itemId":"error-item","quantity":1,"paymentToken":"card-123"}'

# declined card
curl -X POST localhost:8096/api/orders -H 'Content-Type: application/json' \
  -d '{"itemId":"item-1","quantity":1,"paymentToken":"card-decline"}'
# {"status":"PAYMENT_FAILED","paymentId":null,"shipmentId":null}
```

### Approvals

```bash
curl -X POST localhost:8096/api/approvals -H 'Content-Type: application/json' \
  -d '{"orderId":"order-500","amount":15000}'
# 202 {"workflowId":"approval-order-500"}

curl localhost:8096/api/approvals/order-500
# {"result":"PENDING"}

curl -X POST localhost:8096/api/approvals/order-500/approve -H 'Content-Type: application/json' \
  -d '{"approver":"manager-1","comment":"Approved for Q4 budget"}'
curl localhost:8096/api/approvals/order-500
# {"result":"APPROVED"}

# or reject another one
curl -X POST localhost:8096/api/approvals -H 'Content-Type: application/json' -d '{"orderId":"order-501","amount":90000}'
curl -X POST localhost:8096/api/approvals/order-501/reject -H 'Content-Type: application/json' \
  -d '{"approver":"manager-1","comment":"Over budget"}'
```

Against a real server the timeout path really takes 72 hours. That is what the time-skipping test is for.

## Try this

1. **Remove the retry limit.** Delete `.setMaximumAttempts(3)` from `OrderWorkflowImpl` and run `OrderWorkflowCompensationTest`. `reserveStock` now retries forever (Temporal's default), so the refund never happens and the test never finishes (stop it with Ctrl+C). The same order would hang in production. Put the limit back, or give the inventory call its own non-retryable failure type.
2. **Break the deadline.** Change `APPROVAL_TIMEOUT` to 48 hours. `orderWorkflow_timesOutAfter72Hours` still passes, but `approvalJustBeforeTheDeadline_stillCounts` (approves at hour 71) fails. The tests caught a changed business rule in milliseconds.
3. **Replay a saved history against changed code.** Run the tests once, then copy `target/order-workflow-history.json` to `src/test/resources/order-history.json` and add a test that calls `WorkflowReplayer.replayWorkflowExecutionFromResource("order-history.json", OrderWorkflowImpl.class)`. It passes. Now move `inventory.reserveStock(request)` above `payment.charge(request)` in `OrderWorkflowImpl` and run it again: `NonDeterministicException`. (The existing `currentCode_replaysRecordedHistory` still passes, because it records a fresh history from the changed code each run. Replay tests are only useful with histories recorded from the *old* code.)
4. **Test the `error-` path with real activities.** In `OrderWorkflowHappyPathTest`, add a test for `new OrderRequest("error-item", 1, "card-123")`. It expects `REFUNDED` and finishes in well under a second (about 0.6 s on the author's machine, most of it the first workflow task), even though the retries wait 1 s and then 2 s, because the test server skips the back-off.
5. **Add a Spring test for a declined card.** In `OrderWorkflowIntegrationTest`, make `paymentGateway.charge(any())` throw `IllegalArgumentException` and check for `PAYMENT_FAILED` and `verify(inventoryService, never()).reserve(any(), anyInt())`.

## Differences from the slides

- **`@MockitoBean` instead of `@MockBean`** (slide 8). Spring Boot 3.4 deprecates `@MockBean`. `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) does the same job here.
- **Slide 8 also stubs `InventoryService`.** The slide only stubs `paymentGateway.charge`. An unstubbed Mockito mock returns `false` for `reserve(...)`, which the workflow treats as out of stock, so the order would be `REFUNDED`, not `COMPLETED`. The test adds `when(inventoryService.reserve(any(), anyInt())).thenReturn(true)`. The slide's `/* options */` and `/* request */` are filled in with the task queue and `new OrderRequest("item-1", 2, "card-123")`.
- **No Temporal server in the Spring test.** The slide autowires a `WorkflowClient` "from whatever your test profile configures". Here the test profile switches off `TemporalConfig` (`@Profile("!test")`) and `TemporalTestConfig` supplies the client and worker from `TestWorkflowEnvironment`.
- **Slide 2 registers only payment and inventory.** `ShippingActivityImpl` is registered as well, because `item-1` is a physical product and the workflow ships it. Without it, `createShipment` would fail (no worker registered for that activity type), the workflow would refund, and the happy path would end as `REFUNDED` instead of `COMPLETED`.
- **Slide 4 fills in the `...`.** The workflow is started on `"test-queue"` with the environment's client, the test checks for `REFUNDED`, and the environment is closed in a `finally`. The mock throws on every attempt, so the workflow's retry limit (3 attempts) is what lets the test finish. With Temporal's default unlimited retries it would hang (see "Try this" 1).
- **How a product is "digital"** (slide 5) is not shown on the slide. `OrderRequest` has a fourth constructor argument, `new OrderRequest("ebook-1", 1, "card-123", true)`. The three-argument constructor from the slides means a physical product. The slide's `// ... register mocks and run workflow` is done with `TestWorkflowExtension.setDoNotStart(true)`.
- **Slides 6 and 7 use `client`, `worker` and `testEnv` without showing where they come from.** Here they are parameters injected by `TestWorkflowExtension`. Slide 7's "setup workflow with mocked activities" does not apply, because `ApprovalWorkflow` has no activities. Its test keeps the slide's name, `orderWorkflow_timesOutAfter72Hours`, although it tests the approval workflow.
- **`ApprovalWorkflow` has a `reject` signal.** The slides only show `approve`, but the workflow has to be able to return `REJECTED`.
- **Gateway and service shapes.** `PaymentGateway.charge(String paymentToken)` and `InventoryService.reserve(String itemId, int quantity)` are interfaces with in-memory implementations. The slides only show `paymentGateway.charge(any())` returning `ChargeResult("PAY-001")`. `PaymentActivityImpl` and `InventoryActivityImpl` have a no-arg constructor (so `new PaymentActivityImpl()` from slide 2 works, using the in-memory fake) and an `@Autowired` constructor that Spring uses.
- **Failure outcomes are results, not exceptions.** `OrderStatus` has `COMPLETED` plus `PAYMENT_FAILED` and `REFUNDED`, and the workflow returns an `OrderResult` with that status instead of failing. `OrderResult` also carries `getShipmentId()`.
- **Plain `mock(...)` works as activity implementations** with SDK 1.34, as on the slides. No `withSettings().withoutAnnotations()` is needed.
- **Extras not on the slides:** the replay test, `OrderController`, and the second test in most classes.

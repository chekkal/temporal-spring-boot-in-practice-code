# Lecture 15 — Multi-Service Coordination Across Bounded Contexts

Code for Lecture 15 of the course. One order saga runs across four separate Spring Boot applications. `order-service` owns the workflow. Payment, inventory and shipping each run in their own app, and each app has a worker that polls only its own task queue. The only code they share is the `order-api` module.

```
l15-multi-service/
├── order-api/          # shared interfaces + DTOs (plain jar, depends only on temporal-sdk)
├── order-service/      # OrderFulfillmentWorkflowImpl + REST API      port 8092, queue "order-service"
├── payment-service/    # PaymentActivity impl                         port 8093, queue "payment-service"
├── inventory-service/  # InventoryActivity impl                       port 8094, queue "inventory-service"
└── shipping-service/   # ShippingActivity impl                        port 8095, queue "shipping-service"
```

Packages: `com.example.course.l15.api`, `.order`, `.payment`, `.inventory`, `.shipping`.

## Slide to code

| Slide | What it shows | Where it is |
|-------|---------------|-------------|
| 2 — The Multi-Service Challenge | Four Spring Boot apps, order service orchestrates | the four `*-service` modules, each with its own `*ServiceApplication` and `application.yml` |
| 3 — Cross-Service Activities with Task Queues | activity stubs with `.setTaskQueue("payment-service")` / `"inventory-service"` | `order-service/.../order/OrderFulfillmentWorkflowImpl.java` (also a `"shipping-service"` stub) |
| 4 — Each Service Runs Its Own Worker | `paymentWorker` / `inventoryWorker` beans registering only the activity | `payment-service/.../payment/TemporalConfig.java`, `inventory-service/.../inventory/TemporalConfig.java`, `shipping-service/.../shipping/TemporalConfig.java` |
| 5 — Shared Module: The API Contract | `order-api` with the interfaces and DTOs | `order-api/src/main/java/com/example/course/l15/api/` (`OrderFulfillmentWorkflow`, `PaymentActivity`, `InventoryActivity`, `ShippingActivity`, `Order`, `PaymentResult`, `InventoryReservation`, plus `ShipmentResult`, `OrderResult`, `OrderStatus`) |
| 6 — Child Workflows | payment as a child workflow on the `payment-service` queue | not in the code. It is exercise 3 under "Try this" |
| 7 — Namespace Isolation | shared vs separate namespaces | not in the code. All four apps use the `default` namespace (`spring.temporal.namespace`) |
| 8 — The Complete Architecture | the queues and who polls them | the whole module. `order-service/.../order/TemporalConfig.java` registers the workflow only |
| — | the lecture's main claim: a service being down does not fail the order | `order-service/src/test/.../OrderFulfillmentMultiServiceTest.java`, test `inventoryServiceDown_orderWaitsAtInventoryStep_thenCompletesWhenItComesBack` |

## The saga

`OrderFulfillmentWorkflowImpl.fulfill(Order)`:

1. `payment.authorize(order)` on `payment-service`. Registers `payment.refund` as its compensation.
2. `inventory.reserve(order)` on `inventory-service`. Registers `inventory.release`.
3. `shipping.schedule(order, reservation)` on `shipping-service`. Registers `shipping.cancel`.

If an activity fails, the workflow runs the registered compensations one at a time, last one first, then fails. The `getStatus` query returns the current step (`AUTHORIZING_PAYMENT`, `RESERVING_INVENTORY`, `SCHEDULING_SHIPMENT`, `COMPLETED`, `COMPENSATING`, `FAILED`).

The three services keep their data in memory and have failure triggers you can set from the request body:

| Trigger | Service | Failure |
|---------|---------|---------|
| `paymentMethod` contains `decline` | payment-service | `PaymentDeclined`, nothing to compensate |
| an item `sku` starts with `OOS-` | inventory-service | `OutOfStock`, payment refunded |
| `shippingAddress` contains `nowhere` (any case) | shipping-service | `UndeliverableAddress`, inventory released, then payment refunded |

All three are thrown as non-retryable `ApplicationFailure`s, so the saga compensates right away instead of retrying.

## Run the tests

No Temporal server needed. The tests use `TestWorkflowEnvironment`.

```bash
# from the repo root
mvn -B -f course/l15-multi-service/pom.xml verify
```

- `order-service` — `OrderFulfillmentMultiServiceTest` starts one worker per task queue: `order-service` with the workflow only, and `payment-service`, `inventory-service`, `shipping-service` with one activity each. The fake activities write `method@task-queue` to a journal, reading the queue from the activity context, so the tests check which queue ran each step and in what order:
  - happy path runs `authorize@payment-service`, `reserve@inventory-service`, `schedule@shipping-service`
  - inventory fails, and the payment is refunded on `payment-service`
  - shipping fails, and the compensations run in reverse: `release@inventory-service`, then `refund@payment-service`
  - payment declined, and nothing is compensated
  - inventory-service is down. The order waits at `RESERVING_INVENTORY`. A few seconds later it is still `RUNNING`, with `Reserve` pending in state `SCHEDULED`. Then a second `WorkerFactory` starts an inventory worker, the same way a restarted inventory-service would, and the order completes.
- `payment-service`, `inventory-service`, `shipping-service` — a unit test for each activity impl (failure trigger, idempotency, compensation), and a `*WorkerTest` that calls the real `TemporalConfig` worker bean method and checks that a workflow on another queue can reach the activity through `setTaskQueue`.

Why `order-service` tests use fakes and not the real service classes: `order-service` depends only on `order-api`, the same as in production. Depending on the other apps, even with test scope, would couple it to their code. With Maven it would also fail, because `spring-boot-maven-plugin` repackages those jars into executable jars whose classes cannot be put on a classpath. The fakes use the same failure triggers as the real services, and each real service is tested in its own module.

## Run the four apps

Start Temporal once (from the repo root):

```bash
cd order-platform/docker && docker compose up -d
# Temporal UI: http://localhost:8233
```

Build once, then use four terminals, all from the repo root:

```bash
mvn -B -f course/l15-multi-service/pom.xml install -DskipTests

# terminal 1
mvn -f course/l15-multi-service/order-service/pom.xml spring-boot:run
# terminal 2
mvn -f course/l15-multi-service/payment-service/pom.xml spring-boot:run
# terminal 3
mvn -f course/l15-multi-service/inventory-service/pom.xml spring-boot:run
# terminal 4
mvn -f course/l15-multi-service/shipping-service/pom.xml spring-boot:run
```

`install` puts `order-api` in your local Maven repository so each app can be run on its own. Run it again after you change `order-api`.

### Happy path

```bash
curl -X POST localhost:8092/api/orders -H 'Content-Type: application/json' -d '{
  "id": "1501", "customerId": "cust-1",
  "items": [{"sku": "SKU-1", "quantity": 2}],
  "amount": 49.90, "paymentMethod": "card-ok", "shippingAddress": "1 Main St"
}'
# {"orderId":"1501","workflowId":"order-1501"}

curl localhost:8092/api/orders/1501/status
# {"orderId":"1501","status":"COMPLETED"}
```

Each service has a read-only endpoint that shows its own data:

```bash
curl localhost:8093/api/payments       # AUTHORIZED
curl localhost:8094/api/reservations   # RESERVED
curl localhost:8095/api/shipments      # SCHEDULED
```

In the Temporal UI, open `order-1501`. The `ActivityTaskScheduled` events show `payment-service`, `inventory-service` and `shipping-service` as the task queue, and the workflow task events show `order-service`.

### Compensation across services

Shipping fails, so inventory-service releases the stock and then payment-service refunds the payment:

```bash
curl -X POST localhost:8092/api/orders -H 'Content-Type: application/json' -d '{
  "id": "1502", "customerId": "cust-1",
  "items": [{"sku": "SKU-1", "quantity": 1}],
  "amount": 20.00, "paymentMethod": "card-ok", "shippingAddress": "Nowhere Land"
}'

curl localhost:8092/api/orders/1502/status   # FAILED
curl localhost:8094/api/reservations          # res-1502 RELEASED
curl localhost:8093/api/payments              # txn-1502 REFUNDED
```

To make inventory fail instead, use a SKU starting with `OOS-` (for example `"sku": "OOS-1"`). Only the payment is refunded.

Posting the same order id twice returns `409`, because the workflow id is `order-<id>`.

## Try this

1. **Stop inventory-service in the middle of an order.** Stop terminal 3 (Ctrl+C), then post an order `1510`. Its status stays `RESERVING_INVENTORY`. In the Temporal UI the workflow is Running and the Pending Activities tab shows `Reserve` waiting with no attempt started. Wait as long as you want, then start inventory-service again. The order goes on to shipping and completes. Nothing was lost and nobody retried anything by hand. The test `inventoryServiceDown_orderWaitsAtInventoryStep_thenCompletesWhenItComesBack` does the same thing without a server.

2. **Run two payment-service instances.** Start a second one on another port:
   ```bash
   mvn -f course/l15-multi-service/payment-service/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=8193
   ```
   Post several orders (`1520`, `1521`, ...) and watch both terminals: the authorizations are split between the two instances. Both poll the same `payment-service` queue, and order-service needs no change. Each instance keeps its own in-memory data, so `GET /api/payments` on 8093 and 8193 show different payments. A real service would share a database.

3. **Turn payment into a child workflow (slide 6).** Move payment out of the order saga and into its own workflow that runs on the `payment-service` queue:
   - In `order-api`, add a `PaymentWorkflow` interface with `@WorkflowMethod PaymentResult processPayment(Order order)`.
   - In `payment-service`, add `PaymentWorkflowImpl`. It builds its own `PaymentActivity` stub (no `setTaskQueue` needed, it runs on the same queue) and calls `authorize`. Try a second step, such as a fraud check, with its own `Saga` so the child compensates itself.
   - In `payment-service`'s `TemporalConfig`, add `worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class)` to `paymentWorker`. That worker now hosts a workflow and an activity.
   - In `OrderFulfillmentWorkflowImpl`, replace `payment.authorize(order)` with the child stub from slide 6:
     ```java
     PaymentWorkflow paymentWorkflow = Workflow.newChildWorkflowStub(
             PaymentWorkflow.class,
             ChildWorkflowOptions.newBuilder()
                     .setTaskQueue("payment-service")
                     .setWorkflowId("payment-" + order.getId())
                     .build());
     PaymentResult paymentResult = paymentWorkflow.processPayment(order);
     ```
     A failed child shows up in the parent as a `ChildWorkflowFailure`, not an `ActivityFailure`. Change the `catch` to `TemporalFailure`, or catch both. The parent still needs to refund if a later step fails, so keep `saga.addCompensation(payment::refund, paymentResult)`.
   - Run `install` again because `order-api` changed, restart order-service and payment-service, and post an order. The UI now shows `payment-ord-<id>` as a separate workflow with its own history, linked from the parent.
   - In the test, register `PaymentWorkflowImpl` on the `payment-service` worker next to the payment activity.

## Differences from the slides

- **`ShippingActivity` and `shipping-service`.** Slide 5 lists `shipping-service/` but not a `ShippingActivity` file, and slide 3 shows only the payment and inventory stubs. Both are added here, with a third stub on `"shipping-service"` written the same way.
- **Extra DTOs.** Besides `Order`, `PaymentResult` and `InventoryReservation`, `order-api` has `ShipmentResult`, `OrderResult` (the return type used on slide 6) and `OrderStatus` (for the `getStatus` query).
- **Compensation methods.** The slides show only `payment.authorize` and `inventory.reserve`. For the saga, the interfaces also have `refund`, `release` and `cancel`.
- **Hand-written Temporal config.** The slide 4 beans take a `WorkerFactory` from `temporal-spring-boot-starter`. Here, like the rest of this repo, each app has a hand-written `TemporalConfig` (plain `temporal-sdk`) that creates the stubs, client and `WorkerFactory` and starts the factory in an `ApplicationRunner`. The worker beans themselves (`paymentWorker`, `inventoryWorker`, and `shippingWorker` written the same way) match slide 4.
- **Layout root.** Slide 5 puts the modules under `order-platform/`. Here they are under `course/l15-multi-service/`, because `order-platform/` in this repo is the single-service reference app used elsewhere in the course.
- **Read-only endpoints.** `GET /api/payments`, `/api/reservations` and `/api/shipments` are not on the slides. They let you see each service's data from curl.
- **Slide 6 (child workflow)** is not in the code. See exercise 3 above.

# Lecture 14 — Event-Driven Orchestration: Signals, Queries and Updates

Code for Lecture 14 of the course. One `OrderFulfillmentWorkflow` exposes all three ways of talking to a running workflow: two signals, two queries and one update with a validator. A Spring REST controller calls each of them.

Package `com.example.course.l14`, port `8091`, task queue `course-l14`.

## Slide to code

| Slide | What it shows | Where it is |
|-------|---------------|-------------|
| 2 — Three Ways to Interact | signal / query / update table | the whole module. `OrderFulfillmentWorkflow.java` groups the methods by kind |
| 3 — Signals: Async Fire-and-Forget | `fulfill(Order)`, `@SignalMethod updateShippingAddress(Address)`, `@SignalMethod cancelOrder(String)` | `OrderFulfillmentWorkflow.java` |
| 4 — Handling Signals in Workflow Code | `cancelRequested` / `shippingAddress` fields, cancel check after `charge`, `compensatePayment`, shipping with the current `shippingAddress` | `OrderFulfillmentWorkflowImpl.java` (`fulfill`, `cancelOrder`, `updateShippingAddress`, `compensatePayment`) |
| 5 — Queries: Synchronous Read-Only | `getStatus()`, `getDetails()` → `OrderDetails(status, shippingAddress, paymentResult)` | `OrderFulfillmentWorkflow.java`, `OrderFulfillmentWorkflowImpl.java`, `OrderDetails.java` |
| 6 — Updates: Synchronous Mutation with Response | `updateOrderItems` with the `InvalidState` check, `validateUpdateOrderItems` with `ValidationError`, `UpdateOrderResult(total)` | `OrderFulfillmentWorkflowImpl.java`, `UpdateOrderResult.java` |
| 7 — Choosing the Right Mechanism | decision tree | cancel and address are signals, status and details are queries, items is an update, as in the slide's table |
| 8 — Calling from Spring Controllers | `POST /{id}/cancel`, `GET /{id}/status`, `PATCH /{id}/items`, workflow id `"order-" + id` | `OrderController.java` (plus `POST /api/orders`, `PUT /{id}/shipping-address`, `GET /{id}/details`) |

## How the workflow runs

```
PENDING  (60 s edit window)     update items, change address, cancel → CANCELLED, nothing charged
  ↓
CHARGING_PAYMENT                cancel now → refund, CANCELLED
  ↓
RESERVING_INVENTORY             cancel now → release stock + refund, CANCELLED
  ↓
SCHEDULING_SHIPMENT             ships to whatever shippingAddress is at this moment
  ↓
COMPLETED
```

The slides require that items can only change while the order is `PENDING`, but they don't show where that phase comes from. Here `fulfill` starts with `Workflow.await(EDIT_WINDOW, () -> cancelRequested)`: the order stays `PENDING` for 60 seconds (`OrderFulfillmentWorkflowImpl.EDIT_WINDOW`) or until a cancel signal arrives. After that the items are fixed, the order is charged for the current items and total, and any `updateOrderItems` call is rejected with `InvalidState`.

Activities are Spring `@Component` fakes that keep their data in memory. Failure triggers:

| Trigger | Activity | Result |
|---------|----------|--------|
| `paymentMethod` contains `decline` | `PaymentActivityImpl.charge` | `PaymentDeclined`, status `FAILED`, nothing to compensate |
| an item `sku` starts with `oos-` | `InventoryActivityImpl.reserve` | `OutOfStock`, payment refunded, `FAILED` |
| shipping address `zip` is `00000` | `ShippingActivityImpl.schedule` | `UndeliverableAddress`, stock released, payment refunded, `FAILED` |

All three are non-retryable, so compensation starts right away.

## Run the tests

No Temporal server needed. The tests use `TestWorkflowExtension` with the time-skipping test server and Mockito mocks for the three activities.

```bash
# from the repo root
mvn -B -f course/l14-signals-queries-updates/pom.xml verify
```

`OrderFulfillmentWorkflowTest`:

| Test | What it proves |
|------|----------------|
| `update_duringPending_changesTotal_andReturnsItSynchronously` | the update returns the new total straight away, and the charge uses the updated items and total |
| `validator_rejectsEmptyList_andRejectedUpdateIsNotInHistory` | an empty list is rejected by the validator. The history then holds exactly one `WorkflowExecutionUpdateAccepted` and one `UpdateCompleted` (from a valid update sent afterwards) and no event for the rejected one |
| `update_afterPending_isRejectedWithInvalidState` | while the charge is running, the update fails with an `ApplicationFailure` of type `InvalidState` |
| `cancel_duringPending_cancelsWithoutCharging` | cancel in the edit window → `CANCELLED`, `charge` never called |
| `cancel_afterCharge_refundsPayment` | cancel arrives while `charge` runs → `refund` called, no inventory or shipping |
| `addressSignal_beforeShipping_isTheAddressShippedTo` | `shipping.schedule` receives the address from the signal, not the one on the original order |
| `queries_reflectStateMidFlight` | `getStatus` / `getDetails` show `PENDING`, the signalled address, then `RESERVING_INVENTORY` with the payment, and still answer after the workflow has closed |

About time in these tests: the test server's clock only jumps ahead while the test waits on a workflow result (`getResult`) or calls `testEnv.sleep(...)`. In between, it moves at normal speed, so the 60-second edit window stays open while the test sends updates and signals. To test "after PENDING", the tests call `testEnv.sleep(Duration.ofSeconds(61))` and hold the payment or inventory mock on a `CountDownLatch`, so the workflow is stuck in the middle of a step while the test acts on it.

## Run the app

Start Temporal once (from the repo root):

```bash
cd order-platform/docker && docker compose up -d
# Temporal UI: http://localhost:8233
```

Then:

```bash
mvn -f course/l14-signals-queries-updates/pom.xml spring-boot:run
```

The edit window is 60 seconds, so have the commands below ready before you place the order. To get more time, raise `EDIT_WINDOW` in `OrderFulfillmentWorkflowImpl`.

### Place an order

```bash
curl -i -X POST localhost:8091/api/orders -H 'Content-Type: application/json' -d '{
  "id": "1401",
  "customerId": "cust-1",
  "items": [{"sku": "sku-a", "quantity": 2, "unitPrice": 10.00}],
  "shippingAddress": {"street": "1 Main St", "city": "Springfield", "zip": "12345", "country": "US"},
  "paymentMethod": "card-123"
}'
# 202 {"orderId":"1401","workflowId":"order-1401"}
```

### Query it

```bash
curl localhost:8091/api/orders/1401/status     # "PENDING"
curl localhost:8091/api/orders/1401/details    # status, shippingAddress, paymentResult (null until charged)
```

### Edit the items during the pending window (update)

```bash
curl -X PATCH localhost:8091/api/orders/1401/items -H 'Content-Type: application/json' -d '[
  {"sku": "sku-a", "quantity": 1, "unitPrice": 10.00},
  {"sku": "sku-b", "quantity": 3, "unitPrice": 5.50}
]'
# 200 {"total":26.50}   the new total comes back in the same call
```

### Try an invalid update

```bash
curl -i -X PATCH localhost:8091/api/orders/1401/items -H 'Content-Type: application/json' -d '[]'
# 400 {"message":"Order must have at least one item"}   rejected by the validator
```

With the in-memory fakes, the order completes within a second of the window closing, so a `PATCH` sent after the window usually finds a closed workflow and gets `404` (`WorkflowNotFoundException`). To see the handler's `InvalidState` rejection (`409 {"message":"Cannot modify order in state: ..."}`) from curl, slow the workflow down as in "Try this" step 3 and send the `PATCH` during the pause. The test `update_afterPending_isRejectedWithInvalidState` covers it without a server.

### Change the shipping address (signal)

```bash
curl -i -X PUT localhost:8091/api/orders/1401/shipping-address -H 'Content-Type: application/json' -d '{
  "street": "99 Office Park", "city": "Shelbyville", "zip": "54321", "country": "US"
}'
# 202, no body. Check it with /details. When the order ships, the app log shows
# "Scheduled shipment order=1401 to=Address[street=99 Office Park, ...]"
```

### Cancel (signal)

Place a second order and cancel it inside the window:

```bash
curl -X POST localhost:8091/api/orders -H 'Content-Type: application/json' -d '{
  "id": "1402", "customerId": "cust-1",
  "items": [{"sku": "sku-a", "quantity": 1, "unitPrice": 10.00}],
  "shippingAddress": {"street": "1 Main St", "city": "Springfield", "zip": "12345", "country": "US"},
  "paymentMethod": "card-123"
}'
curl -i -X POST localhost:8091/api/orders/1402/cancel -H 'Content-Type: application/json' -d '{"reason": "changed my mind"}'
# 202
curl localhost:8091/api/orders/1402/status      # "CANCELLED", and no "Charged" line in the log
```

Queries still answer after the workflow has closed. Signals and updates to a closed workflow get 404.

## Try this

1. **Compare the three update outcomes in the Temporal UI.** Add the 20-second pause from step 3 first, so the workflow is still running after the window. For one order, send a valid `PATCH` and an empty-list `PATCH` during the window, and another valid `PATCH` during the pause. In the workflow's history, the valid one appears as `WorkflowExecutionUpdateAccepted` + `WorkflowExecutionUpdateCompleted`. The empty list left no trace, because the validator rejected it before anything was written. The late one *is* in history, as an accepted update that completed with an `InvalidState` failure, because that check lives in the update handler (as on slide 6), not in the validator.
2. **Move the `PENDING` check into the validator.** Copy the `status != OrderStatus.PENDING` check into `validateUpdateOrderItems` and repeat step 1. Late updates now leave nothing in history either. Change `update_afterPending_isRejectedWithInvalidState` to match. When would you want a rejected request recorded, and when not?
3. **Cancel after the charge by hand.** The fake charge is instant, so there's no time to cancel during it with curl. Add `Workflow.sleep(Duration.ofSeconds(20));` right after `paymentActivity.charge(order)`, restart, and cancel during those 20 seconds. The log shows `Refunding payment ...` and the status ends as `CANCELLED`. The test `cancel_afterCharge_refundsPayment` does the same with a latch.
4. **Signal a bad address.** During the window, send `{"street":"x","city":"x","zip":"00000","country":"US"}` as the new address. Shipping fails with `UndeliverableAddress`, the reservation is released, the payment refunded, and the status ends as `FAILED`.
5. **Stop the app during the pending window.** Place an order, stop the app with Ctrl+C, wait past 60 seconds, start it again. The timer is kept by the Temporal server, so the order carries on and completes as soon as a worker is back.
6. **Add a discount-code update.** Slide 7 lists "add discount code and confirm final price" as an update. Add `@UpdateMethod UpdateOrderResult applyDiscount(String code)` with a validator that rejects unknown codes, and a `PATCH /{id}/discount` endpoint.

## Differences from the slides

- **Annotations live on the interface.** Slides 5 and 6 show `@QueryMethod`, `@UpdateMethod` and `@UpdateValidatorMethod` on the implementation's methods. The Java SDK only reads them from the `@WorkflowInterface`, so they are on `OrderFulfillmentWorkflow` and the implementation methods are plain `@Override`s.
- **State is initialised in a `@WorkflowInit` constructor.** Slide 4 sets `this.shippingAddress = order.getShippingAddress()` at the top of `fulfill`. Here that line (and the items and total) is in a constructor annotated `@WorkflowInit`, which the SDK runs before any handler. A signal or update can arrive in the same workflow task that starts `fulfill`. With the slide's version, an address signal or items update sent right after the start could be overwritten by `fulfill`'s own assignment.
- **The `PENDING` phase.** The slides check `status != OrderStatus.PENDING` but don't show how the order stays pending. The 60-second `Workflow.await` window described above is added for that, and a cancel during the window ends the order before anything is charged.
- **More cancellation and failure handling.** Slide 4 checks for cancellation once, after the charge, and ends with `// ...`. Here there is a second check after inventory (releases the stock, then refunds), and activity failures are compensated too. That needs `InventoryActivity.release`, which is not on the slides. Also added: `CancelRequest`, `OrderResult`, `PaymentResult`, `InventoryReservation`, `ShipmentResult`, `Address`, `OrderItem`, and more `OrderStatus` values (`CHARGING_PAYMENT`, `RESERVING_INVENTORY`, `SCHEDULING_SHIPMENT`, `COMPLETED`, `CANCELLED`, `FAILED`).
- **The validator's `ValidationError` type does not reach the caller.** With Java SDK 1.34, when a validator throws `ApplicationFailure.newFailure("...", "ValidationError")`, the caller's `WorkflowUpdateException` cause keeps the message but its `getType()` is `io.temporal.internal.worker.WorkflowExecutionException`. The test therefore checks the message, and the controller returns 409 for `InvalidState` and 400 for any other rejection instead of matching on `ValidationError`. The `InvalidState` type thrown from the update handler does arrive intact.
- **Controller additions.** Slide 8 shows `cancel`, `status` and `updateItems` with a field and no constructor. Here there is a constructor, a `POST /api/orders` endpoint that starts the workflow (with workflow id `"order-" + id` and `REJECT_DUPLICATE` so an order id can be used once), `PUT /{id}/shipping-address` for the address signal, `GET /{id}/details` for the details query, and `@ExceptionHandler`s that turn rejected updates, unknown workflows and duplicate ids into 400/409/404 responses.
- **Hand-written Temporal config.** Like the rest of this repo, `TemporalConfig` uses plain `temporal-sdk` (stubs, client, `WorkerFactory`, one worker on `course-l14`, started by an `ApplicationRunner`) instead of `temporal-spring-boot-starter`.

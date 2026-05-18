# order-platform — Reference Application

This is the **reference Spring Boot + Temporal application** for *Temporal with Spring Boot in Practice*. It is the codebase Chapter 25 walks through, and most prior chapters reference pieces of it.

## Layout

```
order-platform/
├── order-api/        # @WorkflowInterface, @ActivityInterface contracts + DTOs (shared)
│   └── src/main/java/com/example/order/api/
│       ├── workflow/    OrderFulfillmentWorkflow, OrderApprovalWorkflow
│       ├── activity/    PaymentActivity, InventoryActivity, ShippingActivity, NotificationActivity
│       └── model/       Order, OrderResult, OrderStatus, PaymentResult, Money, ApprovalDecision, ...
└── order-service/    # Spring Boot app — impls, REST, TemporalConfig, tests
    └── src/main/java/com/example/order/
        ├── OrderServiceApplication.java
        ├── config/TemporalConfig.java
        ├── controller/{OrderController,ApprovalController}.java
        ├── workflow/{OrderFulfillmentWorkflowImpl,OrderApprovalWorkflowImpl}.java
        └── activity/{Payment,Inventory,Shipping,Notification}ActivityImpl.java
```

## Run it

```bash
# 1. Start Temporal (Postgres + server + UI)
cd docker && docker compose up -d
# Temporal UI: http://localhost:8233

# 2. Build + run the service
mvn install
cd order-service && mvn spring-boot:run
# App: http://localhost:8080
```

## Try the happy path

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "ord-001",
    "customerId": "cust-42",
    "items": [{"sku":"SKU-A","quantity":1,"unitPrice":{"amount":19.99,"currency":"USD"}}],
    "total": {"amount":19.99,"currency":"USD"},
    "paymentMethod": "card-good",
    "shippingAddress": "1 Demo St",
    "customerEmail": "demo@example.com"
  }'

curl http://localhost:8080/api/orders/ord-001/status
```

## Try the compensation path

Any payment method containing `decline` forces `PaymentDeclined`:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{ "id":"ord-fail", "paymentMethod":"card-decline-test", "total":{"amount":50,"currency":"USD"}, "items":[{"sku":"X","quantity":1,"unitPrice":{"amount":50,"currency":"USD"}}] }'
```

Authorization fails *before* any compensation is registered, so the saga produces `FAILED` with no rollbacks needed. Modify the workflow to make a later step fail (e.g., throw inside `shipping.schedule`) to see compensations fire in reverse order — payment void → inventory release.

## Approval workflow

```bash
# Approve a running approval workflow (id: "approval-ord-XYZ")
curl -X POST http://localhost:8080/api/approvals/ord-XYZ/approve \
  -H 'Content-Type: application/json' \
  -d '{"approver":"manager-1","comment":"looks good"}'

# Or reject:
curl -X POST http://localhost:8080/api/approvals/ord-XYZ/reject \
  -H 'Content-Type: application/json' \
  -d '{"approver":"manager-1","comment":"price too high"}'
```

## Tests

```bash
cd order-service && mvn test
```

Workflow tests use Temporal's in-process `TestWorkflowExtension` (no server needed) — see Chapter 21.

## Where each chapter lives in the code

| Chapter | Files |
|---|---|
| Ch 13: Order Fulfillment Saga | `OrderFulfillmentWorkflowImpl.java` |
| Ch 14: Payment + Compensation | `PaymentActivity.java`, `PaymentActivityImpl.java` |
| Ch 15: Human-in-the-Loop Approval | `OrderApprovalWorkflowImpl.java`, `ApprovalController.java` |
| Ch 17: Signals/Queries/Updates | `cancel` signal + `getStatus` query on `OrderFulfillmentWorkflow` |
| Ch 25: End-to-End Walkthrough | `TemporalConfig.java`, `OrderController.java`, `application.yml` |
| Ch 26: Retry/Timeout Deep Dive | `defaultActivityOptions()` in `OrderFulfillmentWorkflowImpl` |
| Ch 27: Idempotency + Business Keys | `setWorkflowIdReusePolicy(REJECT_DUPLICATE)` in `OrderController` |

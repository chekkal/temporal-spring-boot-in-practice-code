package com.example.course.l14;

import java.util.List;
import java.util.Map;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.failure.ApplicationFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final WorkflowClient workflowClient;

    public OrderController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /** Start the workflow. Workflow id is "order-" + order id, so a second POST with the same id is rejected. */
    @PostMapping
    public ResponseEntity<Map<String, String>> place(@RequestBody Order order) {
        String workflowId = workflowId(order.getId());
        OrderFulfillmentWorkflow wf = workflowClient.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .build());
        WorkflowClient.start(wf::fulfill, order);
        return ResponseEntity.accepted().body(Map.of("orderId", order.getId(), "workflowId", workflowId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String id,
                                       @RequestBody CancelRequest request) {
        OrderFulfillmentWorkflow wf = workflowClient
                .newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId(id));
        wf.cancelOrder(request.getReason());  // Signal
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{id}/shipping-address")
    public ResponseEntity<Void> updateShippingAddress(@PathVariable String id,
                                                      @RequestBody Address address) {
        OrderFulfillmentWorkflow wf = workflowClient
                .newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId(id));
        wf.updateShippingAddress(address);  // Signal
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<OrderStatus> status(@PathVariable String id) {
        OrderFulfillmentWorkflow wf = workflowClient
                .newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId(id));
        return ResponseEntity.ok(wf.getStatus());  // Query
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<OrderDetails> details(@PathVariable String id) {
        OrderFulfillmentWorkflow wf = workflowClient
                .newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId(id));
        return ResponseEntity.ok(wf.getDetails());  // Query
    }

    @PatchMapping("/{id}/items")
    public ResponseEntity<UpdateOrderResult> updateItems(@PathVariable String id,
                                                         @RequestBody List<OrderItem> items) {
        OrderFulfillmentWorkflow wf = workflowClient
                .newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId(id));
        return ResponseEntity.ok(wf.updateOrderItems(items));  // Update
    }

    private static String workflowId(String orderId) {
        return "order-" + orderId;
    }

    // ------------------------------------------------------------ error mapping

    /**
     * Rejected update. InvalidState (thrown by the update handler) → 409, anything else → 400.
     * We cannot match on "ValidationError": Java SDK 1.34 does not carry the validator's failure
     * type back to the caller (see README), only its message.
     */
    @ExceptionHandler(WorkflowUpdateException.class)
    public ResponseEntity<Map<String, String>> updateRejected(WorkflowUpdateException e) {
        if (e.getCause() instanceof ApplicationFailure af) {
            HttpStatus code = "InvalidState".equals(af.getType()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(code).body(Map.of("message", af.getOriginalMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", String.valueOf(e.getCause())));
    }

    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(WorkflowNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No running order workflow: " + e.getExecution().getWorkflowId()));
    }

    @ExceptionHandler(WorkflowExecutionAlreadyStarted.class)
    public ResponseEntity<Map<String, String>> alreadyStarted(WorkflowExecutionAlreadyStarted e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Order already exists: " + e.getExecution().getWorkflowId()));
    }
}

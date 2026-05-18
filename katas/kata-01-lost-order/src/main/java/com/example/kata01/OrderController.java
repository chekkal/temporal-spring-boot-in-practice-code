package com.example.kata01;

import java.util.List;
import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final WorkflowClient client;

    public OrderController(WorkflowClient client) {
        this.client = client;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> place(@RequestBody Request req) {
        OrderFulfillmentWorkflow workflow = client.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(req.orderId)           // workflow id = order id → dedupe
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());

        WorkflowClient.start(workflow::fulfill, req.orderId, req.items, req.customerEmail);

        return ResponseEntity.accepted().body(Map.of("orderId", req.orderId));
    }

    public static class Request {
        public String orderId;
        public List<String> items;
        public String customerEmail;
    }
}

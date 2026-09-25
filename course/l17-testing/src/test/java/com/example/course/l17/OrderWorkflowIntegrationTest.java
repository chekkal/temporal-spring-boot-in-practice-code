package com.example.course.l17;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Slide 8: full Spring context, external services mocked, real activity beans, Temporal in-process.
 *
 * The slide uses @MockBean; Spring Boot 3.4 deprecates it in favour of @MockitoBean.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TemporalTestConfig.class)
class OrderWorkflowIntegrationTest {

    @MockitoBean
    private PaymentGateway paymentGateway;

    @MockitoBean
    private InventoryService inventoryService;

    @Autowired
    private WorkflowClient workflowClient;

    @Test
    void fullOrderFlow_withSpringContext() {
        when(paymentGateway.charge(any()))
            .thenReturn(new ChargeResult("PAY-001"));
        // Not on the slide: an unstubbed Mockito mock returns false, which the workflow reads as "out of stock".
        when(inventoryService.reserve(any(), anyInt())).thenReturn(true);

        OrderWorkflow workflow = workflowClient.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TemporalConfig.TASK_QUEUE).build());

        OrderResult result = workflow.processOrder(new OrderRequest("item-1", 2, "card-123"));

        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        assertEquals("PAY-001", result.getPaymentId());
        verify(paymentGateway, never()).refund(any());
    }

    @Test
    void outOfStock_refundsThroughTheGateway() {
        when(paymentGateway.charge(any())).thenReturn(new ChargeResult("PAY-002"));
        when(inventoryService.reserve(any(), anyInt())).thenReturn(false);

        OrderWorkflow workflow = workflowClient.newWorkflowStub(OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TemporalConfig.TASK_QUEUE).build());

        OrderResult result = workflow.processOrder(new OrderRequest("item-1", 2, "card-123"));

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
        verify(paymentGateway).refund("PAY-002");
    }
}

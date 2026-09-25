package com.example.course.l17;

/** Boundary to the external payment provider. Mocked with @MockitoBean in OrderWorkflowIntegrationTest. */
public interface PaymentGateway {

    ChargeResult charge(String paymentToken);

    void refund(String paymentId);
}

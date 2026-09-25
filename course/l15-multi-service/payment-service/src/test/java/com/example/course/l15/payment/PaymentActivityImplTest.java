package com.example.course.l15.payment;

import java.math.BigDecimal;
import java.util.List;

import com.example.course.l15.api.Order;
import com.example.course.l15.api.PaymentResult;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentActivityImplTest {

    private final PaymentActivityImpl payment = new PaymentActivityImpl();

    private static Order order(String id, String paymentMethod) {
        return new Order(id, "cust-1", List.of(new Order.LineItem("SKU-1", 1)),
                new BigDecimal("10.00"), paymentMethod, "1 Main St");
    }

    @Test
    void authorize_recordsAnAuthorizedPayment() {
        PaymentResult result = payment.authorize(order("ord-1", "card-ok"));

        assertEquals("txn-ord-1", result.transactionId());
        assertEquals(new BigDecimal("10.00"), result.amount());
        assertEquals("AUTHORIZED", payment.find("txn-ord-1").status());
    }

    @Test
    void authorize_isIdempotentPerOrder() {
        payment.authorize(order("ord-1", "card-ok"));
        payment.authorize(order("ord-1", "card-ok"));

        assertEquals(1, payment.payments().size());
    }

    @Test
    void authorize_declineTriggerFailsWithoutRetry() {
        ApplicationFailure failure = assertThrows(ApplicationFailure.class,
                () -> payment.authorize(order("ord-2", "card-decline")));

        assertEquals("PaymentDeclined", failure.getType());
        assertTrue(failure.isNonRetryable());
        assertTrue(payment.payments().isEmpty());
    }

    @Test
    void refund_marksThePaymentRefunded_andCanRunTwice() {
        PaymentResult result = payment.authorize(order("ord-3", "card-ok"));

        payment.refund(result);
        payment.refund(result);

        assertEquals("REFUNDED", payment.find("txn-ord-3").status());
    }
}

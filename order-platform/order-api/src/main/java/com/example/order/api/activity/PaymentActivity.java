package com.example.order.api.activity;

import com.example.order.api.model.Money;
import com.example.order.api.model.Order;
import com.example.order.api.model.PaymentResult;
import com.example.order.api.model.RefundResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Payment activity (Chapter 14).
 *
 * Separates authorize from capture so compensations can void (free) rather than refund
 * (slow + fee-bearing) when the order fails before capture.
 */
@ActivityInterface
public interface PaymentActivity {

    @ActivityMethod
    void validateOrder(Order order);

    @ActivityMethod
    PaymentResult authorize(Order order);

    @ActivityMethod
    PaymentResult capture(String authorizationId, Money amount);

    @ActivityMethod
    void voidAuthorization(String authorizationId);

    @ActivityMethod
    RefundResult refund(String transactionId, Money amount);

    @ActivityMethod
    PaymentResult charge(Order order);
}

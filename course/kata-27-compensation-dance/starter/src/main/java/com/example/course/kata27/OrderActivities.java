package com.example.course.kata27;

import java.util.List;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface OrderActivities {

    // Step 1
    @ActivityMethod
    void validateOrder(OrderRequest request);
    @ActivityMethod
    void logValidationReversal(OrderRequest request);

    // Step 2
    @ActivityMethod
    PaymentResult chargePayment(OrderRequest request);
    @ActivityMethod
    void refundPayment(String paymentId);

    // Step 3
    @ActivityMethod
    void reserveInventory(OrderRequest request);
    @ActivityMethod
    void releaseInventory(OrderRequest request);

    // Step 4
    @ActivityMethod
    ShipmentResult createShipment(OrderRequest request);
    @ActivityMethod
    void cancelShipment(String trackingId);

    // Step 5 (no compensation: you cannot unsend an email)
    @ActivityMethod
    void sendNotification(OrderRequest request);

    // Called when one or more compensations failed even after their retries
    @ActivityMethod
    void alertOperations(OrderRequest request, List<String> compensationErrors);
}

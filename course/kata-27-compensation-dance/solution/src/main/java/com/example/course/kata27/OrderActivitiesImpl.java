package com.example.course.kata27;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory fakes for the five services. Failure triggers (all driven by the request):
 *
 * <ul>
 *   <li>no items, or amount &lt;= 0            → validateOrder throws "ValidationException" (not retried)</li>
 *   <li>paymentMethod "declined-card"          → chargePayment throws "PaymentDeclinedException" (not retried)</li>
 *   <li>an item named "out-of-stock"           → reserveInventory fails (retried, 3 attempts)</li>
 *   <li>orderId contains "carrier-down"        → createShipment fails (retried, 3 attempts)</li>
 *   <li>orderId contains "email-down"          → sendNotification fails (retried, 3 attempts)</li>
 *   <li>orderId contains "refund-fails"        → refundPayment (a compensation) fails every attempt</li>
 * </ul>
 *
 * The business errors are thrown as {@link ApplicationFailure} with type "ValidationException" /
 * "PaymentDeclinedException", because RetryOptions.setDoNotRetry(...) matches the failure TYPE string.
 * A plain Java exception would get its fully-qualified class name as type and would not match.
 */
@Component
public class OrderActivitiesImpl implements OrderActivities {

    public static final String DECLINED_CARD = "declined-card";
    public static final String OUT_OF_STOCK_ITEM = "out-of-stock";
    public static final String CARRIER_DOWN = "carrier-down";
    public static final String EMAIL_DOWN = "email-down";
    public static final String REFUND_FAILS = "refund-fails";

    private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);

    private final ConcurrentMap<String, PaymentResult> paymentsByOrder = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> chargedPayments = new ConcurrentHashMap<>();   // paymentId → orderId
    private final ConcurrentMap<String, List<String>> reservations = new ConcurrentHashMap<>(); // orderId → items
    private final ConcurrentMap<String, String> shipments = new ConcurrentHashMap<>();         // trackingId → orderId
    private final List<String> operationsAlerts = new CopyOnWriteArrayList<>();

    @Override
    public void validateOrder(OrderRequest request) {
        boolean noItems = request.getItems() == null || request.getItems().isEmpty();
        boolean badAmount = request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0;
        if (noItems || badAmount) {
            throw ApplicationFailure.newFailure(
                    "Invalid order " + request.getOrderId() + ": items and a positive amount are required",
                    "ValidationException");
        }
        log.info("[1] validated order {}", request.getOrderId());
    }

    @Override
    public void logValidationReversal(OrderRequest request) {
        log.info("[-1] validation reversed for order {} (audit log entry)", request.getOrderId());
    }

    @Override
    public PaymentResult chargePayment(OrderRequest request) {
        PaymentResult existing = paymentsByOrder.get(request.getOrderId());
        if (existing != null) {                       // idempotent on retry
            return existing;
        }
        if (DECLINED_CARD.equals(request.getPaymentMethod())) {
            throw ApplicationFailure.newFailure(
                    "Payment declined for order " + request.getOrderId(), "PaymentDeclinedException");
        }
        PaymentResult result = new PaymentResult("pay-" + UUID.randomUUID(), request.getAmount());
        paymentsByOrder.put(request.getOrderId(), result);
        chargedPayments.put(result.getPaymentId(), request.getOrderId());
        log.info("[2] charged {} for order {} → {}", request.getAmount(), request.getOrderId(), result.getPaymentId());
        return result;
    }

    @Override
    public void refundPayment(String paymentId) {
        String orderId = chargedPayments.get(paymentId);
        if (orderId != null && orderId.contains(REFUND_FAILS)) {
            throw new IllegalStateException("Refund service unavailable for " + paymentId);
        }
        if (chargedPayments.remove(paymentId) != null) {
            log.info("[-2] refunded {}", paymentId);
        }
    }

    @Override
    public void reserveInventory(OrderRequest request) {
        if (request.getItems().contains(OUT_OF_STOCK_ITEM)) {
            throw new IllegalStateException("Item out of stock for order " + request.getOrderId());
        }
        reservations.put(request.getOrderId(), List.copyOf(request.getItems()));
        log.info("[3] reserved {} for order {}", request.getItems(), request.getOrderId());
    }

    @Override
    public void releaseInventory(OrderRequest request) {
        if (reservations.remove(request.getOrderId()) != null) {
            log.info("[-3] released inventory for order {}", request.getOrderId());
        }
    }

    @Override
    public ShipmentResult createShipment(OrderRequest request) {
        if (request.getOrderId().contains(CARRIER_DOWN)) {
            throw new IllegalStateException("Carrier unavailable for order " + request.getOrderId());
        }
        String trackingId = "trk-" + UUID.randomUUID();
        shipments.put(trackingId, request.getOrderId());
        log.info("[4] shipment {} created for order {}", trackingId, request.getOrderId());
        return new ShipmentResult(trackingId);
    }

    @Override
    public void cancelShipment(String trackingId) {
        if (shipments.remove(trackingId) != null) {
            log.info("[-4] cancelled shipment {}", trackingId);
        }
    }

    @Override
    public void sendNotification(OrderRequest request) {
        if (request.getOrderId().contains(EMAIL_DOWN)) {
            throw new IllegalStateException("Email service down, cannot notify " + request.getCustomerEmail());
        }
        log.info("[5] notified {} for order {}", request.getCustomerEmail(), request.getOrderId());
    }

    @Override
    public void alertOperations(OrderRequest request, List<String> compensationErrors) {
        operationsAlerts.add(request.getOrderId());
        log.error("[OPS ALERT] order {} needs manual resolution, failed compensations: {}",
                request.getOrderId(), compensationErrors);
    }

    // ── read-only views used by the tests ──
    public boolean isCharged(String paymentId) { return chargedPayments.containsKey(paymentId); }
    public boolean isReserved(String orderId) { return reservations.containsKey(orderId); }
    public boolean hasShipment(String orderId) { return shipments.containsValue(orderId); }
    public List<String> operationsAlerts() { return List.copyOf(operationsAlerts); }
}

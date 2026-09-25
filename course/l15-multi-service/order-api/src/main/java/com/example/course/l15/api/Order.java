package com.example.course.l15.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The order as submitted to order-service and passed to every activity. */
public class Order {

    private String id;
    private String customerId;
    private List<LineItem> items = new ArrayList<>();
    private BigDecimal amount = BigDecimal.ZERO;
    private String paymentMethod;
    private String shippingAddress;

    public Order() {}

    public Order(String id, String customerId, List<LineItem> items, BigDecimal amount,
                 String paymentMethod, String shippingAddress) {
        this.id = id;
        this.customerId = customerId;
        this.items = items;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.shippingAddress = shippingAddress;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<LineItem> getItems() { return items; }
    public void setItems(List<LineItem> items) { this.items = items; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public record LineItem(String sku, int quantity) {}
}

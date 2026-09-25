package com.example.course.kata27;

import java.math.BigDecimal;
import java.util.List;

/** Workflow input. A plain bean (no-arg constructor + getters/setters) so Jackson can (de)serialize it. */
public class OrderRequest {

    private String orderId;
    private String customerEmail;
    private BigDecimal amount;
    private String paymentMethod;
    private List<String> items;

    public OrderRequest() {
    }

    public OrderRequest(String orderId, String customerEmail, BigDecimal amount,
                        String paymentMethod, List<String> items) {
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.items = items;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }

    @Override
    public String toString() {
        return "OrderRequest{orderId=" + orderId + ", amount=" + amount
                + ", paymentMethod=" + paymentMethod + ", items=" + items + "}";
    }
}

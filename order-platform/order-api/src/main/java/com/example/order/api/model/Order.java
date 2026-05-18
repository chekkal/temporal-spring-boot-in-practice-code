package com.example.order.api.model;

import java.util.ArrayList;
import java.util.List;

public class Order {

    private String id;
    private String customerId;
    private List<LineItem> items = new ArrayList<>();
    private Money total = Money.of(0);
    private String paymentMethod;
    private String shippingAddress;
    private String customerEmail;

    public Order() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<LineItem> getItems() { return items; }
    public void setItems(List<LineItem> items) { this.items = items; }

    public Money getTotal() { return total; }
    public void setTotal(Money total) { this.total = total; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public static class LineItem {
        private String sku;
        private int quantity;
        private Money unitPrice;

        public LineItem() {}
        public LineItem(String sku, int quantity, Money unitPrice) {
            this.sku = sku; this.quantity = quantity; this.unitPrice = unitPrice;
        }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public Money getUnitPrice() { return unitPrice; }
        public void setUnitPrice(Money unitPrice) { this.unitPrice = unitPrice; }
    }
}

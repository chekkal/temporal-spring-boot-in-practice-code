package com.example.course.l14;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plain getter/setter class (not a record) so the workflow code reads like the slides:
 * {@code order.getId()}, {@code order.getShippingAddress()}.
 * The total is computed by the workflow from the items; any total sent by the client is ignored.
 */
public class Order {

    private String id;
    private String customerId;
    private List<OrderItem> items;
    private Address shippingAddress;
    private String paymentMethod;
    private BigDecimal total;

    public Order() {}

    public Order(String id, String customerId, List<OrderItem> items,
                 Address shippingAddress, String paymentMethod) {
        this.id = id;
        this.customerId = customerId;
        this.items = items;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}

package com.example.course.l17;

/**
 * Getter/setter class so {@code new OrderRequest("item-1", 2, "card-123")} from the slides compiles
 * and Jackson can (de)serialize it. Digital products are never shipped.
 */
public class OrderRequest {

    private String itemId;
    private int quantity;
    private String paymentToken;
    private boolean digital;

    public OrderRequest() {}

    public OrderRequest(String itemId, int quantity, String paymentToken) {
        this(itemId, quantity, paymentToken, false);
    }

    public OrderRequest(String itemId, int quantity, String paymentToken, boolean digital) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.paymentToken = paymentToken;
        this.digital = digital;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getPaymentToken() { return paymentToken; }
    public void setPaymentToken(String paymentToken) { this.paymentToken = paymentToken; }

    public boolean isDigital() { return digital; }
    public void setDigital(boolean digital) { this.digital = digital; }
}

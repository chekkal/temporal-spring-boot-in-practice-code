package com.example.order.api.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class Money {

    private final BigDecimal amount;
    private final String currency;

    public Money() {
        this(BigDecimal.ZERO, "USD");
    }

    public Money(BigDecimal amount, String currency) {
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.currency = currency == null ? "USD" : currency;
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), "USD");
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }

    @Override public String toString() { return amount + " " + currency; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
    }
    @Override public int hashCode() { return Objects.hash(amount.stripTrailingZeros(), currency); }
}

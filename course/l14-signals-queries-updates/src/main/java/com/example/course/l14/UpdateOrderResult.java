package com.example.course.l14;

import java.math.BigDecimal;

/** Returned synchronously by the {@code updateOrderItems} update. */
public record UpdateOrderResult(BigDecimal total) {}

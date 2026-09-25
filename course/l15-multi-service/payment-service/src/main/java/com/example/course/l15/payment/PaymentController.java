package com.example.course.l15.payment;

import java.util.Collection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of this service's own data, so you can watch refunds happen. */
@RestController
public class PaymentController {

    private final PaymentActivityImpl payments;

    public PaymentController(PaymentActivityImpl payments) {
        this.payments = payments;
    }

    @GetMapping("/api/payments")
    public Collection<PaymentActivityImpl.Payment> list() {
        return payments.payments();
    }
}

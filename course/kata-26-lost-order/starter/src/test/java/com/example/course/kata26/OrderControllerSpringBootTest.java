package com.example.course.kata26;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Full Spring context + REST controller, backed by the in-process Temporal test server. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTemporalConfig.class)
@Timeout(value = 90, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class OrderControllerSpringBootTest {

    @Autowired private MockMvc mvc;
    @Autowired private InventoryActivityImpl inventory;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentGateway gateway;

    @Test
    void happyPath_overRest_andDuplicateOrderIsRejected() throws Exception {
        String body = """
                {"orderId":"rest-1","customerEmail":"a@b.c","amount":99.00,
                 "paymentMethod":"visa","items":["sku-a"]}""";

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflowId").value("order-rest-1"));

        // Same business key → same workflow id → Temporal refuses a second execution.
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        awaitResult("order-rest-1");
        mvc.perform(get("/api/orders/order-rest-1/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/orders/order-rest-1/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("\"COMPLETED\""));
        assertThat(inventory.isReserved("rest-1")).isTrue();

        // Still refused once the first run has completed: REJECT_DUPLICATE reuse policy.
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void shippingFailure_overRest_compensates() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId":"rest-carrier-down","customerEmail":"a@b.c","amount":99.00,
                         "paymentMethod":"visa","items":["sku-a"]}"""))
                .andExpect(status().isAccepted());

        awaitResult("order-rest-carrier-down");
        mvc.perform(get("/api/orders/order-rest-carrier-down/result"))
                .andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(get("/api/orders/order-rest-carrier-down/status"))
                .andExpect(content().string("\"FAILED\""));

        String paymentId = paymentRepository.findByOrderId("rest-carrier-down").orElseThrow().toResult().getPaymentId();
        assertThat(gateway.isCharged(paymentId)).isFalse();
        assertThat(inventory.isReserved("rest-carrier-down")).isFalse();
    }

    private void awaitResult(String workflowId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult r = mvc.perform(get("/api/orders/" + workflowId + "/result")).andReturn();
            if (r.getResponse().getStatus() == 200) {
                return;
            }
        }
        throw new AssertionError("workflow " + workflowId + " did not complete in time");
    }
}

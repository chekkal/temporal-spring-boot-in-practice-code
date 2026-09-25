package com.example.course.kata27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired private OrderActivitiesImpl activities;

    @Test
    void happyPath_overRest() throws Exception {
        placeOrder("rest-1", "visa");

        awaitResult("order-rest-1");
        mvc.perform(get("/api/orders/order-rest-1/result"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/orders/order-rest-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("COMPLETED"))
                .andExpect(jsonPath("$.completedSteps.length()").value(5));
    }

    @Test
    void partialCompensation_overRest_alertsOperations() throws Exception {
        placeOrder("rest-carrier-down-refund-fails", "visa");

        awaitResult("order-rest-carrier-down-refund-fails");
        mvc.perform(get("/api/orders/order-rest-carrier-down-refund-fails/result"))
                .andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(get("/api/orders/order-rest-carrier-down-refund-fails/status"))
                .andExpect(jsonPath("$.currentStep").value("COMPENSATION_PARTIAL"))
                .andExpect(jsonPath("$.completedSteps[3]").value("CREATING_SHIPMENT"));
        assertThat(activities.operationsAlerts()).contains("rest-carrier-down-refund-fails");
    }

    private void placeOrder(String orderId, String paymentMethod) throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId":"%s","customerEmail":"a@b.c","amount":149.00,
                         "paymentMethod":"%s","items":["sku-a"]}""".formatted(orderId, paymentMethod)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflowId").value("order-" + orderId));
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

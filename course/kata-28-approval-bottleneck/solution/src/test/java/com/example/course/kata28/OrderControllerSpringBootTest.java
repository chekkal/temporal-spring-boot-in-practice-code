package com.example.course.kata28;

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
    @Autowired private ApprovalActivitiesImpl approvals;

    @Test
    void bigOrder_waitsForApproval_approvedOverRest() throws Exception {
        placeOrder("rest-big", "7500.00")
                .andExpect(jsonPath("$.approvalRequired").value(true));

        mvc.perform(get("/api/orders/order-rest-big/approval-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("rest-big"))
                .andExpect(jsonPath("$.phase").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.escalated").value(false))
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());

        mvc.perform(post("/api/orders/order-rest-big/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approved":true,"approvedBy":"alice","reason":"Known customer"}"""))
                .andExpect(status().isOk())
                .andExpect(content().string("Approval signal sent"));

        awaitResult("order-rest-big");
        mvc.perform(get("/api/orders/order-rest-big/result"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/orders/order-rest-big/approval-status"))
                .andExpect(jsonPath("$.phase").value("COMPLETED"))
                .andExpect(jsonPath("$.decision.approvedBy").value("alice"));
    }

    @Test
    void smallOrder_isFulfilledDirectly() throws Exception {
        placeOrder("rest-small", "120.00")
                .andExpect(jsonPath("$.approvalRequired").value(false));

        awaitResult("order-rest-small");
        mvc.perform(get("/api/orders/order-rest-small/result"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void bigOrder_withoutAnswer_isRejectedAfter48h() throws Exception {
        placeOrder("rest-timeout", "9000.00");

        // Waiting on the result lets the test server skip the 48 hours.
        awaitResult("order-rest-timeout");
        mvc.perform(get("/api/orders/order-rest-timeout/result"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("Approval timeout"));
        mvc.perform(get("/api/orders/order-rest-timeout/approval-status"))
                .andExpect(jsonPath("$.phase").value("REJECTED_TIMEOUT"))
                .andExpect(jsonPath("$.escalated").value(true));
        assertThat(approvals.escalations()).contains("rest-timeout");
    }

    private org.springframework.test.web.servlet.ResultActions placeOrder(String orderId, String amount) throws Exception {
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"orderId":"%s","customerEmail":"a@b.c","amount":%s,
                         "paymentMethod":"visa","items":["sku-a"]}""".formatted(orderId, amount)))
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

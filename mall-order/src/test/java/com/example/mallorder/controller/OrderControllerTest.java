package com.example.mallorder.controller;

import com.example.mallorder.service.OrderService;
import com.example.mallorder.refund.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new OrderApiExceptionHandler())
                .build();
    }

    @Test
    void listOrdersBindsUserIdFromPath() throws Exception {
        when(orderService.getOrdersByUserId("USER1001")).thenReturn(List.of());

        mockMvc.perform(get("/orders/user/{userId}", "USER1001"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(orderService).getOrdersByUserId("USER1001");
    }

    @Test
    void refundEligibilityReturnsAuthoritativeDecision() throws Exception {
        when(orderService.evaluateRefundEligibility(
                org.mockito.ArgumentMatchers.eq("ORD20260810001"),
                org.mockito.ArgumentMatchers.any(RefundEligibilityCommand.class)))
                .thenReturn(new RefundEligibilityResult(
                        "ORD20260810001", "USER1001", RefundDecision.ELIGIBLE,
                        RefundOperationType.REFUND_ONLY, "refund-v2026.08.18",
                        List.of("PAID_AND_NOT_SHIPPED"), List.of(),
                        RefundNextAction.SUBMIT_REFUND_REQUEST, null, null, List.of()));

        mockMvc.perform(post("/orders/{orderId}/refund-eligibility", "ORD20260810001")
                        .contentType("application/json")
                        .content("""
                                {"userId":"USER1001","reasonType":"NO_REASON"}
                                """))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.decision").value("ELIGIBLE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.reasonCodes[0]").value("PAID_AND_NOT_SHIPPED"));
    }

    @Test
    void afterSalesBusinessRejectionReturnsStableJsonContract() throws Exception {
        RefundEligibilityResult eligibility = new RefundEligibilityResult(
                "ORD20260810003", "USER1002", RefundDecision.INELIGIBLE,
                RefundOperationType.RETURN_AND_REFUND, "refund-v2026.08.18",
                List.of("RETURN_WINDOW_EXPIRED"), List.of(), RefundNextAction.NONE,
                null, null, List.of());
        doThrow(new AfterSalesRejectionException(eligibility))
                .when(orderService).submitAfterSalesRequest(
                        org.mockito.ArgumentMatchers.eq("ORD20260810003"),
                        org.mockito.ArgumentMatchers.any(AfterSalesSubmissionCommand.class));

        mockMvc.perform(post("/orders/{orderId}/after-sales", "ORD20260810003")
                        .contentType("application/json")
                        .content("""
                                {"userId":"USER1002","operationType":"退货"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.errorType").value("BUSINESS_REJECTION"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value("该订单已超过7天退货期限"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.decision").value("INELIGIBLE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.reasonCodes[0]").value("RETURN_WINDOW_EXPIRED"));
    }
}

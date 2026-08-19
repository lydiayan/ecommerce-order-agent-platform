package com.example.mallorder.service;

import com.example.mallorder.entity.AfterSalesRequest;
import com.example.mallorder.entity.Order;
import com.example.mallorder.mapper.AfterSalesRequestMapper;
import com.example.mallorder.mapper.OrderMapper;
import com.example.mallorder.refund.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AfterSalesRequestMapper afterSalesRequestMapper;

    @Mock
    private RefundEligibilityService refundEligibilityService;

    @Test
    void submitRefundPersistsTicketForOwnedPaidOrder() {
        Order order = new Order();
        order.setOrderId("ORD20260810001");
        order.setUserId("USER1001");
        order.setOrderStatus(1);
        when(orderMapper.selectOwnedOrder("ORD20260810001", "USER1001")).thenReturn(order);
        when(orderMapper.selectOrderDetailsByOrderId("ORD20260810001")).thenReturn(List.of());
        when(refundEligibilityService.evaluate(any(), any())).thenReturn(new RefundEligibilityResult(
                "ORD20260810001", "USER1001", RefundDecision.ELIGIBLE,
                RefundOperationType.REFUND_ONLY, RefundEligibilityService.POLICY_VERSION,
                List.of("PAID_AND_NOT_SHIPPED"), List.of(), RefundNextAction.SUBMIT_REFUND_REQUEST,
                null, null, List.of()));
        AfterSalesRequest persisted = new AfterSalesRequest();
        persisted.setTicketId("SR-REFUND-001");
        persisted.setOperationType("退款");
        when(afterSalesRequestMapper.selectByActiveRequestKey("ORD20260810001:退款"))
                .thenReturn(null, persisted);

        OrderService service = new OrderService(orderMapper, afterSalesRequestMapper, refundEligibilityService);
        AfterSalesRequest result = service.submitAfterSalesRequest(
                "ORD20260810001", "USER1001", "退款");

        ArgumentCaptor<AfterSalesRequest> captor = ArgumentCaptor.forClass(AfterSalesRequest.class);
        verify(afterSalesRequestMapper).insertOrKeepExisting(captor.capture());
        assertEquals("USER1001", captor.getValue().getUserId());
        assertEquals("退款", result.getOperationType());
    }

    @Test
    void ownershipMismatchIsReportedAsNotFound() {
        OrderService service = new OrderService(orderMapper, afterSalesRequestMapper, refundEligibilityService);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getOwnedOrder("ORD20260810003", "USER1001"));
        assertEquals(404, error.getStatusCode().value());
    }
}

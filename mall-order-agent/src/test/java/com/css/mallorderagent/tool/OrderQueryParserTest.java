package com.css.mallorderagent.tool;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderQueryParserTest {

    @Test
    void parseOrderId() {
        Optional<String> orderId = OrderQueryParser.parseOrderId("查询订单 ORD20250101120000 物流");
        assertTrue(orderId.isPresent());
        assertEquals("ORD20250101120000", orderId.get());
    }

    @Test
    void parseUserId() {
        Optional<String> userId = OrderQueryParser.parseUserIdFromQuery("查询用户USER1005的订单");
        assertTrue(userId.isPresent());
        assertEquals("USER1005", userId.get());
    }
}

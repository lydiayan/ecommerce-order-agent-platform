package com.example.mallorder.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("demo")
public class DemoOrderResetService {

    private final JdbcTemplate jdbcTemplate;

    public DemoOrderResetService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DemoOrderResetResult reset() {
        int tickets = jdbcTemplate.update("DELETE FROM after_sales_request WHERE user_id IN ('USER1001', 'USER1002')");
        int orders = jdbcTemplate.update("""
                INSERT INTO orders
                    (order_id, user_id, order_time, total_amount, order_status, delivery_status,
                     shipped_at, signed_at,
                     payment_method, shipping_address, contact_phone)
                VALUES
                    ('ORD20260810001', 'USER1001', '2026-08-10 09:15:00', 5999.00, 1, 0,
                     NULL, NULL,
                     'DEMO_PAY', 'DEMO_ADDRESS_001', '13800000001'),
                    ('ORD20260810002', 'USER1001', '2026-08-09 14:30:00', 3299.00, 2, 1,
                     '2026-08-17 10:00:00', NULL,
                     'DEMO_PAY', 'DEMO_ADDRESS_001', '13800000001'),
                    ('ORD20260810003', 'USER1002', '2026-08-08 18:20:00', 8999.00, 3, 2,
                     '2026-08-08 20:00:00', '2026-08-10 09:00:00',
                     'DEMO_PAY', 'DEMO_ADDRESS_002', '13800000002')
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id), order_time = VALUES(order_time),
                    total_amount = VALUES(total_amount), order_status = VALUES(order_status),
                    delivery_status = VALUES(delivery_status), shipped_at = VALUES(shipped_at),
                    signed_at = VALUES(signed_at), production_status = NULL,
                    production_started_at = NULL, digital_delivery_status = NULL,
                    digital_delivered_at = NULL, redeemed_at = NULL,
                    payment_method = VALUES(payment_method), shipping_address = VALUES(shipping_address),
                    contact_phone = VALUES(contact_phone)
                """);
        jdbcTemplate.update("""
                UPDATE order_details
                SET product_type = 0
                WHERE order_id IN ('ORD20260810001', 'ORD20260810002', 'ORD20260810003')
                """);
        return new DemoOrderResetResult(orders, tickets);
    }

    public record DemoOrderResetResult(int affectedOrders, int removedAfterSalesRequests) {
    }
}

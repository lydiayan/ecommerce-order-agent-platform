package com.example.mallorder.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoOrderResetServiceTest {

    @Test
    void clearsTicketsAndRestoresSeedOrders() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(contains("DELETE FROM after_sales_request"))).thenReturn(2);
        when(jdbcTemplate.update(contains("INSERT INTO orders"))).thenReturn(3);

        DemoOrderResetService.DemoOrderResetResult result =
                new DemoOrderResetService(jdbcTemplate).reset();

        assertEquals(3, result.affectedOrders());
        assertEquals(2, result.removedAfterSalesRequests());
    }
}

package com.example.mallorder.mapper;

import com.example.mallorder.entity.Order;
import com.example.mallorder.entity.OrderDetail;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Select("SELECT * FROM orders WHERE order_id = #{orderId}")
    Order selectOrderById(@Param("orderId") String orderId);

    @Select("SELECT * FROM orders WHERE order_id = #{orderId} AND user_id = #{userId}")
    Order selectOwnedOrder(@Param("orderId") String orderId, @Param("userId") String userId);

    @Select("""
            SELECT detail_id AS detailId,
                   order_id AS orderId,
                   product_id AS productId,
                   product_name AS productName,
                   product_type AS productType,
                   quantity,
                   unit_price AS unitPrice,
                   total_price AS totalPrice,
                   specification
            FROM order_details
            WHERE order_id = #{orderId}
            """)
    List<OrderDetail> selectOrderDetailsByOrderId(@Param("orderId") String orderId);

    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY order_time DESC")
    List<Order> selectOrdersByUserId(@Param("userId") String userId);

    @Update("UPDATE orders SET order_status = 4 " +
            "WHERE order_id = #{orderId} AND user_id = #{userId} AND order_status IN (0, 1)")
    int cancelOrder(@Param("orderId") String orderId, @Param("userId") String userId);
}

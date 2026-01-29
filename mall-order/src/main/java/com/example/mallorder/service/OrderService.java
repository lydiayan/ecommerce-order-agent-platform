package com.example.mallorder.service;

import com.example.mallorder.entity.Order;
import com.example.mallorder.entity.OrderDetail;
import com.example.mallorder.entity.Product;
import com.example.mallorder.mapper.OrderMapper;
import com.example.mallorder.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
public class OrderService {
    @Autowired
    private OrderMapper orderMapper;

    public List<Order> getOrders() {
        List<Order> orders = orderMapper.selectAllOrders();
        for (Order order : orders) {
            List<OrderDetail> details = orderMapper.selectOrderDetailsByOrderId(order.getOrderId());
            order.setOrderDetails(details);
        }
        return orders;
    }

    public Order getOrderById(String orderId) {
        Order order = orderMapper.selectOrderById(orderId);
        if (order != null) {
            List<OrderDetail> details = orderMapper.selectOrderDetailsByOrderId(orderId);
            order.setOrderDetails(details);
        }
        return order;
    }

    public List<Order> getOrdersByUserId(String userId) {
        List<Order> orders = orderMapper.selectOrdersByUserId(userId);
        for (Order order : orders) {
            List<OrderDetail> details = orderMapper.selectOrderDetailsByOrderId(order.getOrderId());
            order.setOrderDetails(details);
        }
        return orders;
    }

    public boolean cancelOrder(String orderId) {
        return orderMapper.cancelOrder(orderId) > 0;
    }

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductMapper productMapper;

    public Order createOrder(String userId, int productId) {
        // 获取商品信息
        List<Product> products = productMapper.getProductsById(productId);
        if (products.isEmpty()) {
            throw new RuntimeException("商品不存在");
        }

        Product product = products.get(0);

        // 检查库存
        if (product.getStock_quantity() <= 0) {
            throw new RuntimeException("商品库存不足");
        }

        // 创建订单
        Order order = new Order();
        order.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        String orderId = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        order.setOrderId("ORD" + orderId);
        Date date = Date.from(now.atZone(ZoneId.systemDefault()).toInstant());
        order.setOrderTime(date);
        order.setTotalAmount(product.getPrice());
        order.setOrderStatus(0);
        // 保存订单
        orderMapper.insertOrder(order);
        // 保存订单详情
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(order.getOrderId());
        orderDetail.setProductId(String.valueOf(productId));
        orderDetail.setProductName(product.getProduct_name());
        orderDetail.setQuantity(1);
        orderDetail.setUnitPrice(product.getPrice());
        orderDetail.setTotalPrice(product.getPrice());
        orderMapper.insertOrderDetail(orderDetail);

        // 减少商品库存
        product.setStock_quantity(product.getStock_quantity() - 1);
        productMapper.updateProduct(product);

        return order;
    }

}
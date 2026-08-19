package com.css.mallorderagent.tool;

import com.css.mallorderagent.tool.dto.MallOrderDetailDto;
import com.css.mallorderagent.tool.dto.MallOrderDto;

import java.text.SimpleDateFormat;
import java.util.List;

final class OrderResultFormatter {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private OrderResultFormatter() {
    }

    static String formatOrder(MallOrderDto order) {
        if (order == null) {
            return "未找到该订单。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("订单号：").append(nullToEmpty(order.getOrderId())).append('\n');
        sb.append("用户ID：").append(nullToEmpty(order.getUserId())).append('\n');
        sb.append("下单时间：").append(formatDate(order)).append('\n');
        sb.append("订单状态：").append(formatStatus(order.getOrderStatus())).append('\n');
        sb.append("物流状态：").append(formatDeliveryStatus(order.getDeliveryStatus())).append('\n');
        if (order.getShippedAt() != null) {
            sb.append("发货时间：").append(order.getShippedAt()).append('\n');
        }
        if (order.getSignedAt() != null) {
            sb.append("签收时间：").append(order.getSignedAt()).append('\n');
        }
        sb.append("订单金额：").append(order.getTotalAmount() != null ? order.getTotalAmount() : "-").append('\n');
        if (order.getShippingAddress() != null && !order.getShippingAddress().isBlank()) {
            sb.append("收货地址：").append(order.getShippingAddress()).append('\n');
        }
        if (order.getContactPhone() != null && !order.getContactPhone().isBlank()) {
            sb.append("联系电话：").append(order.getContactPhone()).append('\n');
        }
        appendDetails(sb, order.getOrderDetails());
        return sb.toString().trim();
    }

    static String formatOrders(List<MallOrderDto> orders, String userId) {
        if (orders == null || orders.isEmpty()) {
            return "用户 " + userId + " 暂无订单记录。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户 ").append(userId).append(" 共有 ").append(orders.size()).append(" 笔订单：\n\n");
        for (int i = 0; i < orders.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(formatOrderSummary(orders.get(i))).append('\n');
        }
        sb.append("\n如需查看某笔订单详情，请提供订单号。");
        return sb.toString().trim();
    }

    private static String formatOrderSummary(MallOrderDto order) {
        return nullToEmpty(order.getOrderId()) + " | "
                + formatStatus(order.getOrderStatus()) + " | 金额 "
                + (order.getTotalAmount() != null ? order.getTotalAmount() : "-")
                + " | " + formatDate(order);
    }

    private static void appendDetails(StringBuilder sb, List<MallOrderDetailDto> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        sb.append("商品明细：\n");
        for (MallOrderDetailDto detail : details) {
            sb.append("- ").append(nullToEmpty(detail.getProductName()))
                    .append(" x").append(detail.getQuantity() != null ? detail.getQuantity() : 1)
                    .append("，单价 ").append(detail.getUnitPrice() != null ? detail.getUnitPrice() : "-")
                    .append("，商品类型：").append(formatProductType(detail.getProductType()))
                    .append('\n');
        }
    }

    private static String formatProductType(Integer productType) {
        if (productType == null) {
            return "未知";
        }
        return switch (productType) {
            case 0 -> "普通商品";
            case 1 -> "定制商品";
            case 2 -> "生鲜类";
            case 3 -> "虚拟商品";
            default -> "未知(" + productType + ")";
        };
    }

    private static String formatDeliveryStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "未发货";
            case 1 -> "运输中";
            case 2 -> "已签收";
            case 3 -> "已拒收";
            default -> "未知(" + status + ")";
        };
    }

    private static String formatStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待付款";
            case 1 -> "已付款";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知(" + status + ")";
        };
    }

    private static String formatDate(MallOrderDto order) {
        if (order.getOrderTime() == null) {
            return "-";
        }
        synchronized (DATE_FORMAT) {
            return DATE_FORMAT.format(order.getOrderTime());
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}

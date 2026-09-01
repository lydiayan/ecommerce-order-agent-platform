package com.example.mallordercmpserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.mallordercmpserver.data.Order;
import com.example.mallordercmpserver.data.RefundEligibilityResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author: jyy
 * @Desc:
 **/
@Service
public class OpenOrderService {

    private static final Logger log = LoggerFactory.getLogger(OpenOrderService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenOrderService(WebClient.Builder webClientBuilder,
                            @Value("${mall-order.base-url:http://127.0.0.1:8081}") String baseUrl,
                            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 通过订单服务查询指定用户的订单列表。
     *
     * @param userId 用户编号
     * @return 用户订单；下游返回空响应时返回空列表
     */
    @Tool(description = "根据用户ID获取该用户的订单列表")
    public List<Order> getOrdersByUserId(String userId) {
        List<Order> response = webClient.get()
                .uri("/orders/user/{userId}", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Order>>() {
                })
                .block();
        return response != null ? response : List.of();
    }

    /**
     * 通过订单服务按订单号和用户归属查询订单详情。
     *
     * @param orderId 订单编号
     * @param userId 当前用户编号
     * @return 当前用户拥有的订单详情
     */
    @Tool(description = "按订单ID查询当前用户拥有的订单详情")
    public Order getOrderById(String orderId, String userId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}")
                        .queryParam("userId", userId)
                        .build(orderId))
                .retrieve()
                .bodyToMono(Order.class)
                .block();
    }

    /**
     * 调用订单规则服务评估整单退款资格，并格式化为约束模型决策的权威文本。
     *
     * @param orderId 订单编号
     * @param userId 当前用户编号
     * @param reasonType 退款原因类型，可为空
     * @param customerOpened 用户是否声明已拆封，可为空
     * @param customerUsed 用户是否声明已使用，可为空
     * @param conditionStatus 商品是否可二次销售，可为空
     * @param reasonDescription 问题描述，可为空
     * @param evidenceUrls 证据地址，可为空
     * @return 包含资格结论、理由、缺失字段和规则版本的文本
     */
    @Tool(description = "权威判断当前用户整单退款资格。返回四态结论、原因编码、缺失字段和下一步动作；不得用知识库推翻该结果")
    public String evaluateRefundEligibility(
            String orderId,
            String userId,
            @ToolParam(required = false, description = "NO_REASON/QUALITY_ISSUE/WRONG_ITEM/SHIPPING_DAMAGE/OTHER")
            String reasonType,
            @ToolParam(required = false, description = "用户声明是否拆封") Boolean customerOpened,
            @ToolParam(required = false, description = "用户声明是否使用") Boolean customerUsed,
            @ToolParam(required = false, description = "RESALABLE/NOT_RESALABLE") String conditionStatus,
            @ToolParam(required = false, description = "质量问题或异常情况描述") String reasonDescription,
            @ToolParam(required = false, description = "证据文件地址列表") List<String> evidenceUrls) {
        RefundEligibilityResult result = webClient.post()
                .uri("/orders/{orderId}/refund-eligibility", orderId)
                .bodyValue(new RefundEligibilityRequest(
                        userId,
                        reasonType != null ? reasonType : "NO_REASON",
                        customerOpened,
                        customerUsed,
                        conditionStatus,
                        reasonDescription,
                        evidenceUrls != null ? evidenceUrls : List.of()))
                .retrieve()
                .bodyToMono(RefundEligibilityResult.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("mall-order returned an empty eligibility response"));
        return formatEligibility(result);
    }

    /**
     * 调用订单服务取消当前用户拥有且状态允许取消的订单。
     *
     * @param orderId 订单编号
     * @param userId 当前用户编号
     * @return 下游明确返回成功时为 {@code true}
     */
    @Tool(description = "取消当前用户拥有的订单")
    public boolean cancelOrder(String orderId, String userId) {
        Boolean result = webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/orders/{orderId}/cancel")
                        .queryParam("userId", userId)
                        .build(orderId))
                .retrieve()
                .bodyToMono(Boolean.class)
                .block();
        return Boolean.TRUE.equals(result);
    }

    /**
     * 提交退货、退款或换货申请。订单规则拒绝会转换为结构化工具结果，
     * 网络错误或无法识别的下游错误仍作为技术异常抛出。
     *
     * @param orderId 订单编号
     * @param userId 当前用户编号
     * @param operationType 售后类型
     * @return 成功工单信息或包含真实拒绝原因的结构化结果
     */
    @Tool(description = "为当前用户订单提交退货、退款或换货申请，返回结构化的成功或业务拒绝结果")
    public AfterSalesToolResult submitAfterSalesRequest(@ToolParam(description = "订单id")String orderId, String userId, String operationType) {
        try {
            AfterSalesTicket ticket = createAfterSalesTicket(orderId, userId, operationType);
            String message = """
                    已成功提交%s申请，当前状态为%s。
                    - 订单号：%s
                    - 工单号：%s
                    后续可在「我的订单」查看进度，客服将在 1 个工作日内处理。
                    """.formatted(ticket.operationType(), ticket.status(), ticket.orderId(), ticket.ticketId()).trim();
            return AfterSalesToolResult.success(message);
        } catch (WebClientResponseException exception) {
            AfterSalesRejectionResponse rejection = parseBusinessRejection(exception);
            log.warn("mall-order after-sales response status={}, businessRejection={}",
                    exception.getStatusCode().value(), rejection != null);
            if (rejection == null) {
                throw exception;
            }
            return AfterSalesToolResult.rejected(rejection);
        }
    }

    /**
     * 为当前用户订单提交修改收货地址申请。
     *
     * @param orderId 订单编号
     * @param userId 当前用户编号
     * @return 包含订单号和售后工单号的确认文本
     */
    @Tool(description = "为当前用户订单提交修改收货地址申请")
    public String submitAddressChangeRequest(String orderId, String userId) {
        AfterSalesTicket ticket = createAfterSalesTicket(orderId, userId, "修改收货地址");
        return """
                已提交修改收货地址申请。
                - 订单号：%s
                - 工单号：%s
                请留意客服或短信通知确认新地址。
                """.formatted(ticket.orderId(), ticket.ticketId()).trim();
    }

    private AfterSalesTicket createAfterSalesTicket(String orderId, String userId, String operationType) {
        return webClient.post()
                .uri("/orders/{orderId}/after-sales", orderId)
                .bodyValue(new AfterSalesCommand(userId, operationType))
                .retrieve()
                .bodyToMono(AfterSalesTicket.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("mall-order returned an empty after-sales response"));
    }

    private record AfterSalesCommand(String userId, String operationType) {
    }

    private record RefundEligibilityRequest(
            String userId,
            String reasonType,
            Boolean customerOpened,
            Boolean customerUsed,
            String conditionStatus,
            String reasonDescription,
            List<String> evidenceUrls) {
    }

    private record AfterSalesTicket(String ticketId, String orderId, String userId,
                                    String operationType, String status) {
    }

    private AfterSalesRejectionResponse parseBusinessRejection(WebClientResponseException exception) {
        String body = exception.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            AfterSalesRejectionResponse response = objectMapper.readValue(body, AfterSalesRejectionResponse.class);
            return "BUSINESS_REJECTION".equals(response.errorType()) ? response : null;
        } catch (IOException exceptionDuringParsing) {
            log.warn("Unable to parse mall-order after-sales error body as business rejection, bodyLength={}",
                    body.length());
            return null;
        }
    }

    public record AfterSalesToolResult(
            boolean success,
            String message,
            String failureType,
            String decision,
            List<String> reasonCodes,
            List<String> missingFields,
            String nextAction,
            String policyVersion) {

        static AfterSalesToolResult success(String message) {
            return new AfterSalesToolResult(true, message, null, null,
                    List.of(), List.of(), null, null);
        }

        static AfterSalesToolResult rejected(AfterSalesRejectionResponse rejection) {
            return new AfterSalesToolResult(false, rejection.message(), rejection.errorType(),
                    rejection.decision(), safeList(rejection.reasonCodes()), safeList(rejection.missingFields()),
                    rejection.nextAction(), rejection.policyVersion());
        }
    }

    private record AfterSalesRejectionResponse(
            String errorType,
            String code,
            String message,
            String decision,
            String operationType,
            List<String> reasonCodes,
            List<String> missingFields,
            String nextAction,
            String policyVersion) {
    }

    private static List<String> safeList(List<String> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    private static String formatEligibility(RefundEligibilityResult result) {
        String amount = result.getRefundableAmount() != null ? result.getRefundableAmount().toPlainString() : "-";
        String operation = result.getOperationType() != null ? result.getOperationType() : "-";
        return """
                【退款资格权威结论】
                订单号：%s
                资格结论：%s
                退款业务：%s
                原因编码：%s
                缺失字段：%s
                下一步：%s
                可申请金额：%s
                规则版本：%s
                约束：该结论由订单规则服务根据实时数据库事实计算，知识库和大模型不得修改结论。
                """.formatted(
                result.getOrderId(),
                result.getDecision(),
                operation,
                result.getReasonCodes() != null ? result.getReasonCodes() : List.of(),
                result.getMissingFields() != null ? result.getMissingFields() : List.of(),
                result.getNextAction(),
                amount,
                result.getPolicyVersion()).trim();
    }
}

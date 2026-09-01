package com.css.mallorderagent.demo;

import com.css.mallorderagent.tool.client.MallOrderClient;
import com.css.mallorderagent.tool.dto.MallOrderDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class DemoPersonaService {

    private final DemoPersonaRepository repository;
    private final MallOrderClient mallOrderClient;

    public DemoPersonaService(DemoPersonaRepository repository, MallOrderClient mallOrderClient) {
        this.repository = repository;
        this.mallOrderClient = mallOrderClient;
    }

    /**
     * 查询所有启用的演示身份，并补齐其知识范围、能力和推荐问题。
     *
     * @return 可供身份选择界面展示的演示身份
     */
    public List<DemoPersonaView> findAll() {
        return repository.findAllActive().stream().map(this::toView).toList();
    }

    /**
     * 查询指定启用的演示身份，不存在或参数为空时返回对应 HTTP 错误。
     *
     * @param actorUserId 业务身份编号
     * @return 完整演示身份视图
     */
    public DemoPersonaView requirePersona(String actorUserId) {
        return toView(requireRow(actorUserId));
    }

    /**
     * 将演示身份转换为 Agent 执行所需的授权上下文和身份提示词。
     * 客户只能访问自己，销售只能访问明确分配的客户。
     *
     * @param actorUserId 业务身份编号
     * @return 包含能力、客户范围和知识范围的执行上下文
     */
    public DemoActorContext resolveActor(String actorUserId) {
        DemoPersonaView persona = requirePersona(actorUserId);
        List<String> customerIds = switch (persona.category()) {
            case CUSTOMER -> List.of(persona.actorUserId());
            case SALES -> repository.findAssignedCustomerIds(persona.actorUserId());
            default -> List.of();
        };
        String prompt = """
                当前认证身份：%s（%s，%s）。
                所属类别：%s；职责说明：%s。
                只能使用认证服务授予的知识范围和业务能力，不得扩大数据访问范围。
                """.formatted(persona.displayName(), persona.jobTitle(), persona.department(),
                persona.category(), persona.description()).trim();
        return new DemoActorContext(
                persona.actorUserId(), prompt, persona.capabilities(), customerIds,
                persona.roleScopes(), persona.departmentScopes());
    }

    /**
     * 组装演示工作台数据；员工视角的客户订单会隐藏手机号和地址。
     *
     * @param actorUserId 业务身份编号
     * @return 身份信息、可见订单、知识范围和推荐问题
     */
    public DemoWorkspace getWorkspace(String actorUserId) {
        DemoPersonaView persona = requirePersona(actorUserId);
        List<DemoWorkspace.CustomerOrders> customers = switch (persona.category()) {
            case CUSTOMER -> List.of(customerOrders(persona.actorUserId(), persona.displayName(), false));
            case SALES -> repository.findAssignedCustomerIds(persona.actorUserId()).stream()
                    .map(customerId -> customerOrders(
                            customerId,
                            repository.findActiveById(customerId).map(DemoPersonaRepository.PersonaRow::displayName)
                                    .orElse(customerId),
                            true))
                    .toList();
            default -> List.of();
        };
        List<String> knowledgeScopes = new ArrayList<>();
        persona.roleScopes().forEach(scope -> knowledgeScopes.add("角色：" + scope));
        persona.departmentScopes().forEach(scope -> knowledgeScopes.add("部门：" + scope));
        return new DemoWorkspace(
                persona,
                persona.category().name(),
                customers,
                List.copyOf(knowledgeScopes),
                persona.suggestions());
    }

    /**
     * 查询全部业务身份编号，主要用于演示环境批量重置。
     *
     * @return 排序后的身份编号
     */
    public List<String> findAllActorUserIds() {
        return repository.findAllActorUserIds();
    }

    private DemoWorkspace.CustomerOrders customerOrders(String customerId, String customerName, boolean masked) {
        List<MallOrderDto> orders = mallOrderClient.getOrdersByUserId(customerId);
        if (masked) {
            orders = orders.stream().map(DemoPersonaService::maskOrderForStaff).toList();
        }
        return new DemoWorkspace.CustomerOrders(customerId, customerName, orders);
    }

    private DemoPersonaView toView(DemoPersonaRepository.PersonaRow row) {
        return new DemoPersonaView(
                row.actorUserId(), row.category(), row.displayName(), row.jobTitle(), row.department(),
                row.description(), row.welcomeMessage(),
                repository.findScopes(row.actorUserId(), "ROLE"),
                repository.findScopes(row.actorUserId(), "DEPARTMENT"),
                repository.findCapabilities(row.actorUserId()),
                repository.findSuggestions(row.actorUserId()));
    }

    private DemoPersonaRepository.PersonaRow requireRow(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actorUserId must not be blank");
        }
        return repository.findActiveById(actorUserId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "demo persona not found"));
    }

    /**
     * 复制订单并隐藏员工视角不应直接展示的手机号和收货地址。
     *
     * @param source 原始订单
     * @return 脱敏后的订单副本
     */
    public static MallOrderDto maskOrderForStaff(MallOrderDto source) {
        MallOrderDto masked = new MallOrderDto();
        masked.setOrderId(source.getOrderId());
        masked.setUserId(source.getUserId());
        masked.setOrderTime(source.getOrderTime());
        masked.setTotalAmount(source.getTotalAmount());
        masked.setOrderStatus(source.getOrderStatus());
        masked.setDeliveryStatus(source.getDeliveryStatus());
        masked.setShippedAt(source.getShippedAt());
        masked.setSignedAt(source.getSignedAt());
        masked.setProductionStatus(source.getProductionStatus());
        masked.setProductionStartedAt(source.getProductionStartedAt());
        masked.setDigitalDeliveryStatus(source.getDigitalDeliveryStatus());
        masked.setDigitalDeliveredAt(source.getDigitalDeliveredAt());
        masked.setRedeemedAt(source.getRedeemedAt());
        masked.setPaymentMethod(source.getPaymentMethod());
        masked.setShippingAddress(maskAddress(source.getShippingAddress()));
        masked.setContactPhone(maskPhone(source.getContactPhone()));
        masked.setOrderDetails(source.getOrderDetails());
        return masked;
    }

    private static String maskPhone(String value) {
        if (value == null || value.length() < 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String maskAddress(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        return value.substring(0, Math.min(4, value.length())) + "***";
    }
}

package com.css.mallorderagent.demo;

import com.css.mallorderagent.tool.dto.MallOrderDto;

import java.util.List;

public record DemoWorkspace(
        DemoPersonaView persona,
        String workspaceType,
        List<CustomerOrders> customers,
        List<String> knowledgeScopes,
        List<String> suggestions) {

    public record CustomerOrders(
            String customerUserId,
            String customerName,
            List<MallOrderDto> orders) {
    }
}

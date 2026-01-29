package com.example.mallordergraphserver01.tools;

import com.example.mallordergraphserver01.config.OrderMcpNodeProperties;

import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MallMcpClientToolCallbackProvider {
    private final ToolCallbackProvider toolCallbackProvider;


    private final OrderMcpNodeProperties orderMcpNodeProperties;

    private final McpClientCommonProperties mcpClientCommonProperties;


    public MallMcpClientToolCallbackProvider(ToolCallbackProvider toolCallbackProvider,
                                             McpClientCommonProperties commonProperties, OrderMcpNodeProperties orderMcpNodeProperties){
        this.toolCallbackProvider = toolCallbackProvider;
        this.mcpClientCommonProperties = commonProperties;
        this.orderMcpNodeProperties = orderMcpNodeProperties;
    }

    public Set<ToolCallback> getToolCallbacks(String nodeName) {
        Set<ToolCallback> defineCallback = new HashSet<>();
        List<String> mcpClients =orderMcpNodeProperties.getNode2servers().get(nodeName);
        if(mcpClients == null || mcpClients.size() ==0){
            return defineCallback;
        }
        List<String> exceptMcpClientNames =  new ArrayList<>();
        for(String mcpClientName : mcpClients){
            //my-mcp-client
            String name=mcpClientCommonProperties.getName();
            // my_mcp_client_server1
            String prefixedMcpClientName= McpToolUtils.prefixedToolName(name,mcpClientName);
            exceptMcpClientNames.add(prefixedMcpClientName);
        }

        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        for(ToolCallback toolCallback : toolCallbacks){
            ToolDefinition toolDefinition = toolCallback.getToolDefinition();
            String name = toolDefinition.name();
            for (String exceptMcpClientName : exceptMcpClientNames) {
                if(exceptMcpClientName.equals(name)){
                    defineCallback.add(toolCallback);
                }
            }
        }
        return defineCallback;
    }
}

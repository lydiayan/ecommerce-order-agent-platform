package com.css.mallorderagent.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.css.mallorderagent.dto.AskRequest;
import com.css.mallorderagent.memory.ConversationTurn;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchRequest;
import com.example.mallordermilvusrag.dto.SearchResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph 节点共用的状态读取与转换工具。
 */
public final class AgentGraphSupport {

    public static final String DEFAULT_SESSION_ID = "default";
    public static final String NO_CONTEXT_ANSWER = "知识库中未找到与您问题相关的资料，请换个问法或联系相关部门。";

    private AgentGraphSupport() {
    }

    public static AskRequest requireAskRequest(OverAllState state) {
        return state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class)
                .orElseThrow(() -> new IllegalStateException("askRequest is required in graph state"));
    }

    public static String resolveQuery(OverAllState state) {
        return state.value(AgentGraphKeys.QUERY, String.class)
                .or(() -> state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class).map(AskRequest::getQuery))
                .map(String::trim)
                .filter(query -> !query.isBlank())
                .orElseThrow(() -> new IllegalStateException("query must not be blank"));
    }

    public static String resolveSessionId(OverAllState state) {
        return state.value(AgentGraphKeys.SESSION_ID, String.class)
                .filter(id -> !id.isBlank())
                .or(() -> state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class)
                        .map(AskRequest::getConversationId))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .orElse(DEFAULT_SESSION_ID);
    }

    public static String resolveUserId(OverAllState state, String defaultUserId) {
        return state.value(AgentGraphKeys.USER_ID, String.class)
                .filter(id -> !id.isBlank())
                .or(() -> state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class)
                        .map(AskRequest::getUserId))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .orElse(defaultUserId);
    }

    @SuppressWarnings("unchecked")
    public static List<ConversationTurn> readHistory(OverAllState state) {
        return state.value(AgentGraphKeys.HISTORY, List.class).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public static List<String> readStringList(OverAllState state, String key) {
        return state.value(key, List.class).orElse(List.of()).stream()
                .map(String::valueOf)
                .toList();
    }

    public static boolean hasCapability(OverAllState state, String capability) {
        return readStringList(state, AgentGraphKeys.CAPABILITIES).contains(capability);
    }

    public static boolean hasCapabilityContext(OverAllState state) {
        return state.data().containsKey(AgentGraphKeys.CAPABILITIES);
    }

    public static List<ConversationTurn> toConversationTurns(List<Message> messages) {
        List<ConversationTurn> turns = new ArrayList<>();
        String pendingUser = null;
        for (Message message : messages) {
            if (message instanceof UserMessage userMessage) {
                pendingUser = userMessage.getText();
            } else if (message instanceof AssistantMessage assistantMessage && pendingUser != null) {
                turns.add(new ConversationTurn(pendingUser, assistantMessage.getText()));
                pendingUser = null;
            }
        }
        return turns;
    }

    public static String buildContext(List<SearchResponse.SearchHit> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            SearchResponse.SearchHit hit = hits.get(i);
            sb.append("[").append(i + 1).append("] ");
            DocumentMetadata meta = hit.getMetadata();
            if (meta != null && meta.getSource() != null && !meta.getSource().isBlank()) {
                sb.append("来源：").append(meta.getSource()).append('\n');
            }
            sb.append(hit.getContent() != null ? hit.getContent().trim() : "").append("\n\n");
        }
        return sb.toString().trim();
    }

    public static SearchRequest toSearchRequest(AskRequest request, RagDocumentProperties.AskProperties askProperties) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery(request.getQuery());
        searchRequest.setTopK(request.getTopK() > 0 ? request.getTopK() : askProperties.getContextTopK());
        searchRequest.setSimilarityThreshold(request.getSimilarityThreshold());
        searchRequest.setSourceFilter(request.getSourceFilter());
        searchRequest.setDepartmentFilter(request.getDepartmentFilter());
        searchRequest.setRoleFilter(request.getRoleFilter());
        searchRequest.setVersionFilter(request.getVersionFilter());
        searchRequest.setEnableRerank(request.getEnableRerank());
        searchRequest.setRerankTopN(request.getRerankTopN());
        searchRequest.setRecallTopK(request.getRecallTopK());
        searchRequest.setRerankMinScore(request.getRerankMinScore());
        return searchRequest;
    }

    @SuppressWarnings("unchecked")
    public static SearchRequest toSearchRequest(AskRequest request,
                                                RagDocumentProperties.AskProperties askProperties,
                                                OverAllState state) {
        SearchRequest searchRequest = toSearchRequest(request, askProperties);
        List<String> roles = state.value(AgentGraphKeys.RAG_ROLE_SCOPES, List.class).orElse(null);
        List<String> departments = state.value(AgentGraphKeys.RAG_DEPARTMENT_SCOPES, List.class).orElse(null);
        if (roles != null || departments != null) {
            searchRequest.setRoleFilter(null);
            searchRequest.setDepartmentFilter(null);
            searchRequest.setRoleFilters(roles != null ? roles : List.of());
            searchRequest.setDepartmentFilters(departments != null ? departments : List.of());
        }
        return searchRequest;
    }
}

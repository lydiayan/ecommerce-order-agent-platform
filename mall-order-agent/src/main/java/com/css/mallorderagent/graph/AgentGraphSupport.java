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

    /**
     * 从 Graph 状态读取原始问答请求。
     *
     * @param state 当前 Graph 状态
     * @return 原始问答请求
     * @throws IllegalStateException 状态中缺少请求时抛出
     */
    public static AskRequest requireAskRequest(OverAllState state) {
        return state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class)
                .orElseThrow(() -> new IllegalStateException("askRequest is required in graph state"));
    }

    /**
     * 优先读取规范化 QUERY 字段，并回退到原始请求中的用户问题。
     *
     * @param state 当前 Graph 状态
     * @return 去除首尾空白后的用户问题
     * @throws IllegalStateException 问题不存在或为空时抛出
     */
    public static String resolveQuery(OverAllState state) {
        return state.value(AgentGraphKeys.QUERY, String.class)
                .or(() -> state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class).map(AskRequest::getQuery))
                .map(String::trim)
                .filter(query -> !query.isBlank())
                .orElseThrow(() -> new IllegalStateException("query must not be blank"));
    }

    /**
     * 解析会话编号，缺失时使用固定的 default 会话。
     *
     * @param state 当前 Graph 状态
     * @return 状态字段、原始请求或默认值中的会话编号
     */
    public static String resolveSessionId(OverAllState state) {
        return state.value(AgentGraphKeys.SESSION_ID, String.class)
                .filter(id -> !id.isBlank())
                .or(() -> state.value(AgentGraphKeys.ASK_REQUEST, AskRequest.class)
                        .map(AskRequest::getConversationId))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .orElse(DEFAULT_SESSION_ID);
    }

    /**
     * 解析业务用户编号，状态和原始请求均缺失时返回调用方默认值。
     *
     * @param state 当前 Graph 状态
     * @param defaultUserId 回退使用的业务用户编号
     * @return 解析后的业务用户编号
     */
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
    /**
     * 读取 Graph 状态中的历史对话。
     *
     * @param state 当前 Graph 状态
     * @return 历史问答列表；字段不存在时返回空列表
     */
    public static List<ConversationTurn> readHistory(OverAllState state) {
        return state.value(AgentGraphKeys.HISTORY, List.class).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    /**
     * 将 Graph 状态中的列表字段安全转换为字符串列表。
     *
     * @param state 当前 Graph 状态
     * @param key 待读取的状态键
     * @return 字符串列表；字段不存在时返回空列表
     */
    public static List<String> readStringList(OverAllState state, String key) {
        return state.value(key, List.class).orElse(List.of()).stream()
                .map(String::valueOf)
                .toList();
    }

    /**
     * 判断当前 Graph 身份上下文是否包含指定能力。
     *
     * @param state 当前 Graph 状态
     * @param capability 能力枚举名称
     * @return 能力列表包含该名称时返回 true
     */
    public static boolean hasCapability(OverAllState state, String capability) {
        return readStringList(state, AgentGraphKeys.CAPABILITIES).contains(capability);
    }

    /**
     * 判断 Graph 是否显式携带能力上下文，以区分未授权和兼容模式。
     *
     * @param state 当前 Graph 状态
     * @return CAPABILITIES 键存在时返回 true
     */
    public static boolean hasCapabilityContext(OverAllState state) {
        return state.data().containsKey(AgentGraphKeys.CAPABILITIES);
    }

    /**
     * 将 Spring AI 消息序列配对为用户和助手问答轮次。
     *
     * @param messages 按时间顺序排列的聊天消息
     * @return 仅包含完整“用户-助手”配对的对话轮次
     */
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

    /**
     * 将检索命中格式化为带序号和来源的 Prompt 参考资料。
     *
     * @param hits RAG 检索命中列表
     * @return 适合写入 Prompt 的证据文本
     */
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

    /**
     * 将 Agent 问答参数转换为 RAG 搜索请求，并应用默认召回数量。
     *
     * @param request Agent 问答请求
     * @param askProperties RAG 问答默认参数
     * @return 包含过滤、阈值和重排配置的搜索请求
     */
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
    /**
     * 构造 RAG 搜索请求，并用服务端授权范围覆盖不可信的客户端角色和部门过滤条件。
     *
     * @param request Agent 问答请求
     * @param askProperties RAG 问答默认参数
     * @param state 包含授权角色和部门范围的 Graph 状态
     * @return 已施加服务端授权过滤的搜索请求
     */
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

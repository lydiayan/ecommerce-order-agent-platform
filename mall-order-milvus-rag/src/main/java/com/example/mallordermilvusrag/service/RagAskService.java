package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.AskResponse;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.SearchRequest;
import com.example.mallordermilvusrag.dto.SearchResponse;
import com.example.mallordermilvusrag.tracing.PromptBuildSpanAttributes;
import com.example.mallordermilvusrag.tracing.RagTraceOperations;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import com.example.mallorderobservability.trace.TracePrivacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG 问答：检索 + rerank + Qwen 生成。
 */
@Service
public class RagAskService {

    private static final Logger log = LoggerFactory.getLogger(RagAskService.class);

    private static final String NO_CONTEXT_ANSWER = "知识库中未找到与您问题相关的资料，请换个问法或联系相关部门。";

    private final RagService ragService;
    private final ChatClient chatClient;
    private final RagDocumentProperties.AskProperties askProperties;
    private final RagTraceService ragTraceService;
    private final ObjectProvider<RagTracingAdvisor> ragTracingAdvisorProvider;

    public RagAskService(RagService ragService,
                           ChatClient chatClient,
                           RagDocumentProperties ragDocumentProperties,
                           RagTraceService ragTraceService,
                           ObjectProvider<RagTracingAdvisor> ragTracingAdvisorProvider) {
        this.ragService = ragService;
        this.chatClient = chatClient;
        this.askProperties = ragDocumentProperties.getAsk();
        this.ragTraceService = ragTraceService;
        this.ragTracingAdvisorProvider = ragTracingAdvisorProvider;
    }

    public AskResponse ask(SearchRequest request) {
        if (!ragTraceService.isEnabled()) {
            return askInternal(request, RagTraceScope.noop());
        }

        String query = request.getQuery();
        Map<String, Object> attrs = Map.of(
                "queryLength", query != null ? query.length() : 0,
                "queryFingerprint", TracePrivacy.fingerprint(query));
        try (RagTraceScope trace = ragTraceService.begin("rag.ask", attrs)) {
            AskResponse response = askInternal(request, trace);
            response.setTraceId(trace.traceId());
            if (response.getRetrieval() != null) {
                response.getRetrieval().setTraceId(trace.traceId());
            }
            trace.attribute("grounded", response.isGrounded());
            return response;
        }
    }

    private AskResponse askInternal(SearchRequest request, RagTraceScope trace) {
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        SearchRequest searchRequest = copyForRetrieval(request);
        SearchResponse retrieval;
        try (RagTraceScope retrieveSpan = trace.child(RagTraceOperations.RETRIEVE)) {
            retrieval = ragService.search(searchRequest, retrieveSpan);
            retrieveSpan.attribute("hitCount", retrieval.getTotalHits());
        }

        if (retrieval.getHits() == null || retrieval.getHits().isEmpty()) {
            log.info("Ask: no retrieval hits, queryLength={}", query.length());
            return new AskResponse(query, NO_CONTEXT_ANSWER, false, retrieval);
        }
        int contextLimit = Math.min(
                Math.max(askProperties.getContextTopK(), 1),
                retrieval.getHits().size());
        List<SearchResponse.SearchHit> contextHits = retrieval.getHits().subList(0, contextLimit);
        String context = buildContext(contextHits);
        String userMessage = buildUserMessage(context, query);
        String systemPrompt = askProperties.getSystemPrompt();

        try (RagTraceScope promptSpan = trace.child(RagTraceOperations.PROMPT_BUILD,
                PromptBuildSpanAttributes.build(
                        askProperties.getPromptVersion(),
                        systemPrompt,
                        userMessage,
                        contextHits.size(),
                        0,
                        0))) {
            // 同步记录 prompt 组装信息
        }

        log.info("Ask: generating answer with {} context chunk(s), model={}",
                contextHits.size(), askProperties.getModel());

        String answer;
        RagTracingAdvisor ragTracingAdvisor = ragTracingAdvisorProvider.getIfAvailable();
        RagTracingAdvisor.tag("queryLength", query.length());
        RagTracingAdvisor.tag("contextChunks", contextHits.size());
        RagTracingAdvisor.bindParentScope(trace);
        try {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(askProperties.getModel())
                            .temperature(askProperties.getTemperature())
                            .build())
                    .user(userMessage);
            if (ragTracingAdvisor != null) {
                requestSpec.advisors(ragTracingAdvisor);
            }
            answer = requestSpec.call().content();
        } finally {
            RagTracingAdvisor.clearParentScope();
            RagTracingAdvisor.clearTags();
        }

        return new AskResponse(query, answer, true, retrieval);
    }

    static String buildContext(List<SearchResponse.SearchHit> hits) {
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

    static String buildUserMessage(String context, String query) {
        return """
                参考资料：
                %s

                用户问题：%s

                请仅根据参考资料回答。若资料不足以回答，请明确说明。
                """.formatted(context, query.trim());
    }

    private SearchRequest copyForRetrieval(SearchRequest request) {
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
}

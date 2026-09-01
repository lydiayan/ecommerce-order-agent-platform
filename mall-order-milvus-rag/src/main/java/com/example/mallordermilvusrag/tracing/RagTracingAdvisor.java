package com.example.mallordermilvusrag.tracing;

import com.example.mallordermilvusrag.tracing.RagTraceOperations;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.ChatOptions;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring AI {@link BaseAdvisor} that automatically creates {@code llm}
 * spans within an active RAG trace.
 *
 * <p>Business code can pass additional attributes via the static
 * {@link #tag(String, Object)} / {@link #clearTags()} ThreadLocal side-channel
 * before invoking {@code ChatClient}.
 */
public class RagTracingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(RagTracingAdvisor.class);

    static final String SPAN_KEY = "_rag_trace_llm_span";

    private static final ThreadLocal<Map<String, Object>> CURRENT_TAGS =
            ThreadLocal.withInitial(HashMap::new);

    /** 调用 ChatClient 前由业务层绑定的父 trace 作用域（解决 Advisor 线程读不到 ThreadLocal 栈的问题） */
    private static final ThreadLocal<RagTraceScope> PARENT_SCOPE = new ThreadLocal<>();

    private final RagTraceService ragTraceService;

    public RagTracingAdvisor(RagTraceService ragTraceService) {
        this.ragTraceService = ragTraceService;
    }

    // ── static side-channel ─────────────────────────────────────────────

    /**
     * Set a tag that will be picked up by the next {@code before()} call
     * and attached as a span attribute.
     */
    public static void tag(String key, Object value) {
        CURRENT_TAGS.get().put(key, value);
    }

    /**
     * Clear all pending tags. Call this after the ChatClient call completes.
     */
    public static void clearTags() {
        CURRENT_TAGS.get().clear();
    }

    /**
     * 在调用 {@code ChatClient} 前绑定当前 RAG trace 父作用域。
     */
    public static void bindParentScope(RagTraceScope parentScope) {
        if (parentScope != null && parentScope != RagTraceScope.noop()) {
            PARENT_SCOPE.set(parentScope);
        }
    }

    /**
     * 清除当前线程绑定的父 Trace 作用域，防止线程复用时串联无关请求。
     */
    public static void clearParentScope() {
        PARENT_SCOPE.remove();
    }

    /**
     * 获取当前绑定的父 trace 作用域；未绑定时返回 {@link RagTraceScope#noop()}。
     */
    public static RagTraceScope parentScope() {
        RagTraceScope scope = PARENT_SCOPE.get();
        return scope != null ? scope : RagTraceScope.noop();
    }


    // ── Advisor identity ─────────────────────────────────────────────────


    @Override
    public String getName() {
        return "rag-tracing";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    // ── Hooks ────────────────────────────────────────────────────────────

    /**
     * 包装同步模型调用，使异常也会标记并关闭 LLM Span。
     *
     * @param request ChatClient 请求
     * @param chain 同步调用链
     * @return ChatClient 响应
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        if (!ragTraceService.isEnabled()) {
            return request;
        }

        RagTraceScope parentScope = PARENT_SCOPE.get();
        if (parentScope == null && ragTraceService.currentTraceId() == null) {
            return request;
        }

        Map<String, Object> tags = new LinkedHashMap<>(CURRENT_TAGS.get());
        CURRENT_TAGS.get().clear();

        String model = null;
        Double temperature = null;
        Integer inputLength = null;
        if (request.prompt() != null) {
            Map<String, Object> promptAttrs = PromptTraceAttributes.fromPrompt(request.prompt());
            Object promptLength = promptAttrs.get("promptLength");
            if (promptLength instanceof Number number) {
                inputLength = number.intValue();
            }
            if (request.prompt().getOptions() != null) {
                ChatOptions options = request.prompt().getOptions();
                model = options.getModel();
                temperature = options.getTemperature();
            }
        }

        Integer queryLength = null;
        if (tags.containsKey("queryLength")) {
            Object value = tags.get("queryLength");
            if (value instanceof Number number) {
                queryLength = number.intValue();
            }
        }
        Integer contextChunks = null;
        if (tags.containsKey("contextChunks")) {
            Object value = tags.get("contextChunks");
            if (value instanceof Number number) {
                contextChunks = number.intValue();
            }
        }

        Map<String, Object> attrs = LlmSpanAttributes.buildStartAttributes(
                queryLength, contextChunks, model, temperature, inputLength);

        RagTraceScope span = parentScope != null
                ? parentScope.child(RagTraceOperations.LLM, attrs)
                : ragTraceService.childSpan(RagTraceOperations.LLM, attrs);

        Map<String, Object> newCtx = new LinkedHashMap<>(request.context());
        newCtx.put(SPAN_KEY, span);
        return ChatClientRequest.builder()
                .prompt(request.prompt())
                .context(newCtx)
                .build();
    }

    /**
     * 包装流式模型调用，在错误或流结束时关闭 LLM Span。
     *
     * @param chain 流式调用链
     * @return 带 Trace 生命周期处理的响应流
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        Object spanObj = response.context().get(SPAN_KEY);
        if (spanObj instanceof RagTraceScope span) {
            try {
                enrichLlmSpan(span, response);
            } finally {
                span.close();
            }
        }

        Map<String, Object> cleaned = new LinkedHashMap<>(response.context());
        cleaned.remove(SPAN_KEY);
        return ChatClientResponse.builder()
                .chatResponse(response.chatResponse())
                .context(cleaned)
                .build();
    }

    private void enrichLlmSpan(RagTraceScope span, ChatClientResponse response) {
        if (response.chatResponse() == null) {
            return;
        }
        span.attributes(LlmSpanAttributes.fromChatResponse(response.chatResponse()));
    }

    // ── Override adviseCall for error handling ───────────────────────────

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest modified = before(request, chain);
        try {
            ChatClientResponse response = chain.nextCall(modified);
            return after(response, chain);
        } catch (RuntimeException e) {
            Object spanObj = modified.context().get(SPAN_KEY);
            if (spanObj instanceof RagTraceScope span) {
                span.error(e);
                span.close();
            }
            throw e;
        }
    }

    // ── Streaming (pass-through with basic error handling) ───────────────

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest modified = before(request, chain);
        return chain.nextStream(modified)
                .doOnError(e -> {
                    Object spanObj = modified.context().get(SPAN_KEY);
                    if (spanObj instanceof RagTraceScope span) {
                        span.error((Throwable) e);
                        span.close();
                    }
                })
                .doOnComplete(() -> {
                    // Streaming path: after() is called for each response;
                    // the last one should close the span.  We hook into the last
                    // response via doOnNext, but for simplicity we rely on doOnComplete
                    // as a safety net.
                    Object spanObj = modified.context().get(SPAN_KEY);
                    if (spanObj instanceof RagTraceScope span) {
                        try {
                            span.close();
                        } catch (Exception ignored) {
                            // span may already be closed
                        }
                    }
                });
    }
}

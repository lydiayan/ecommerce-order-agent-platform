package com.css.mallorderagent.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.css.mallorderagent.graph.AgentGraphKeys;
import com.css.mallorderagent.graph.AgentGraphSupport;
import com.css.mallorderagent.planner.HumanApprovalDetector;
import com.css.mallorderagent.planner.PlanResult;
import com.css.mallorderagent.prompt.BuiltPrompt;
import com.css.mallorderagent.stream.AgentStreamDisconnectedException;
import com.css.mallorderagent.stream.AgentStreamSessionRegistry;
import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.tracing.LlmSpanAttributes;
import com.example.mallordermilvusrag.tracing.RagTracingAdvisor;
import com.example.mallorderobservability.trace.RagTraceScope;
import com.example.mallorderobservability.trace.RagTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 节点：调用 ChatClient 生成回答。
 * <p>
 * {@code DANGEROUS_ORDER_OP} 策略下使用模板确认话术，不调用 LLM，避免复读订单列表。
 * </p>
 */
@Component
public class LlmNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(LlmNode.class);

    public static final String NODE_NAME = "llm";

    private final ChatClient chatClient;
    private final RagDocumentProperties.AskProperties askProperties;
    private final AgentStreamSessionRegistry streamRegistry;

    public LlmNode(ChatClient chatClient,
                   RagDocumentProperties ragDocumentProperties,
                   AgentStreamSessionRegistry streamRegistry) {
        this.chatClient = chatClient;
        this.askProperties = ragDocumentProperties.getAsk();
        this.streamRegistry = streamRegistry;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String query = AgentGraphSupport.resolveQuery(state);
        PlanResult plan = state.value(AgentGraphKeys.PLAN, PlanResult.class).orElse(null);
        String planStrategy = plan != null ? plan.strategy()
                : state.value(AgentGraphKeys.PLAN_STRATEGY, "");
        boolean dangerousOrderOp = HumanApprovalDetector.isDangerousOrderOp(planStrategy);

        String answer;
        if (dangerousOrderOp) {
            String toolResult = state.value(AgentGraphKeys.TOOL_RESULT, "");
            String context = state.value(AgentGraphKeys.CONTEXT, "");
            answer = HumanApprovalDetector.buildDangerousOrderConfirmation(query, toolResult, context);
            log.info("LlmNode skipped LLM for DANGEROUS_ORDER_OP, queryLength={}, orderIdPresent={}",
                    query.length(), HumanApprovalDetector.extractFirstOrderId(toolResult).isPresent());
        } else {
            BuiltPrompt built = state.value(AgentGraphKeys.BUILT_PROMPT, BuiltPrompt.class)
                    .orElseThrow(() -> new IllegalStateException("builtPrompt is required before LlmNode"));
            int contextChunks = state.value(AgentGraphKeys.CONTEXT_HIT_COUNT, 0);
            RagTraceScope trace = RagTracingAdvisor.parentScope();
            String streamId = state.value(AgentGraphKeys.STREAM_ID, "");
            answer = streamId.isBlank()
                    ? callLlm(trace, query, built, contextChunks)
                    : streamLlm(trace, query, built, contextChunks, streamId);
            log.info("LlmNode completed, strategy={}, answerLength={}", planStrategy, answer.length());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(AgentGraphKeys.ANSWER, answer);
        boolean needsReview = dangerousOrderOp
                || state.value(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, false)
                || HumanApprovalDetector.queryRequiresApproval(query)
                || HumanApprovalDetector.answerRequiresApproval(answer);
        if (needsReview) {
            updates.put(AgentGraphKeys.HUMAN_APPROVAL_REQUIRED, true);
            updates.put(AgentGraphKeys.APPROVAL_REASON,
                    HumanApprovalDetector.resolveReason(query, answer));
        }
        return updates;
    }

    private String callLlm(RagTraceScope trace, String query, BuiltPrompt built, int contextChunks) {
        Map<String, Object> startAttributes = LlmSpanAttributes.buildStartAttributes(
                query.length(),
                contextChunks,
                askProperties.getModel(),
                askProperties.getTemperature(),
                built.systemPrompt().length() + built.userMessage().length());

        try (RagTraceScope llmSpan = trace.child(RagTraceService.LLM_OPERATION, startAttributes)) {
            try {
                ChatClient.ChatClientRequestSpec requestSpec = requestSpec(built, false);
                ChatResponse response = requestSpec.call().chatResponse();
                llmSpan.attributes(LlmSpanAttributes.fromChatResponse(response));
                llmSpan.attribute("streaming", false);
                return extractAnswer(response);
            } catch (RuntimeException e) {
                llmSpan.error(e);
                throw e;
            }
        }
    }

    private String streamLlm(RagTraceScope trace, String query, BuiltPrompt built,
                             int contextChunks, String streamId) {
        Map<String, Object> startAttributes = LlmSpanAttributes.buildStartAttributes(
                query.length(),
                contextChunks,
                askProperties.getModel(),
                askProperties.getTemperature(),
                built.systemPrompt().length() + built.userMessage().length());

        try (RagTraceScope llmSpan = trace.child(RagTraceService.LLM_OPERATION, startAttributes)) {
            long startedAt = System.nanoTime();
            AtomicLong firstTokenLatencyMillis = new AtomicLong(-1);
            AtomicLong chunkCount = new AtomicLong();
            AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
            StringBuilder answer = new StringBuilder();
            try {
                requestSpec(built, true).stream().chatResponse()
                        .takeUntilOther(streamRegistry.cancellationSignal(streamId))
                        .doOnNext(response -> {
                            lastResponse.set(response);
                            String delta = extractDelta(response);
                            if (delta.isEmpty()) {
                                return;
                            }
                            if (firstTokenLatencyMillis.compareAndSet(-1,
                                    (System.nanoTime() - startedAt) / 1_000_000)) {
                                log.debug("First streamed answer chunk received, latencyMs={}",
                                        firstTokenLatencyMillis.get());
                            }
                            answer.append(delta);
                            chunkCount.incrementAndGet();
                            streamRegistry.emitDelta(streamId, delta);
                        })
                        .blockLast();

                if (streamRegistry.isCancelled(streamId)) {
                    throw new AgentStreamDisconnectedException("SSE client disconnected during generation");
                }
                if (answer.isEmpty()) {
                    throw new IllegalStateException("LLM returned an empty answer");
                }
                llmSpan.attributes(LlmSpanAttributes.fromChatResponse(lastResponse.get()));
                llmSpan.attribute("streaming", true);
                llmSpan.attribute("ttftMs", firstTokenLatencyMillis.get());
                llmSpan.attribute("firstTokenLatencyMs", firstTokenLatencyMillis.get());
                llmSpan.attribute("chunkCount", chunkCount.get());
                llmSpan.attribute("outputLength", answer.length());
                return answer.toString();
            } catch (RuntimeException e) {
                llmSpan.error(e);
                throw e;
            }
        }
    }

    private ChatClient.ChatClientRequestSpec requestSpec(BuiltPrompt built, boolean streaming) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(askProperties.getModel())
                .temperature(askProperties.getTemperature());
        if (streaming) {
            options.streamUsage(true);
        }
        return chatClient.prompt()
                .options(options.build())
                .system(built.systemPrompt())
                .user(built.userMessage());
    }

    private static String extractDelta(ChatResponse response) {
        Generation generation = response != null ? response.getResult() : null;
        if (generation == null || generation.getOutput() == null) {
            return "";
        }
        String text = generation.getOutput().getText();
        return text != null ? text : "";
    }

    private static String extractAnswer(ChatResponse response) {
        Generation generation = response != null ? response.getResult() : null;
        if (generation == null || generation.getOutput() == null) {
            throw new IllegalStateException("LLM returned no generation");
        }
        String answer = generation.getOutput().getText();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("LLM returned an empty answer");
        }
        return answer;
    }
}

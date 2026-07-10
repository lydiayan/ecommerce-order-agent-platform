package com.example.mallordermemory.service;

import com.example.mallordermemory.memory.MemoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆提取器
 * <p>
 * 采用规则预过滤 + LLM 精提取的两阶段策略：
 * <ul>
 *   <li><b>规则层</b> — 正则匹配常见模式（用户偏好、否定、陈述）</li>
 *   <li><b>LLM 层</b> — 将初步候选发送给 LLM 进行语义理解和结构化输出</li>
 * </ul>
 * </p>
 */
public class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    private final OpenAiChatModel chatModel;

    // ==================== 规则层：预过滤模式 ====================

    /** 用户偏好表达模式 */
    private static final List<Pattern> PREFERENCE_PATTERNS = List.of(
            Pattern.compile("我(?:喜欢|偏爱|倾向于|更想|希望|不想|不喜欢|讨厌)(.{2,60})"),
            Pattern.compile("(?:帮我|给我|叫我|称呼我)(.{2,30})"),
            Pattern.compile("我(?:是|是一名?|是位|做)(.{2,40})"),  // 职业/角色
            Pattern.compile("我的(?:名字|名称|称呼|职位|角色|爱好|习惯)(?:是|为)(.{2,40})"),
            Pattern.compile("(?:请|要|想)用(.{2,30})(?:方式|方法|风格|格式)")
    );

    /** 事实性表达模式 */
    private static final List<Pattern> FACT_PATTERNS = List.of(
            Pattern.compile("(?:我的|我们公司|我们的|公司)(?:的)?(.{5,100})"),
            Pattern.compile("(?:订单|商品|价格|物流|退款|退货|售后|客服)(.{5,100})"),
            Pattern.compile("(?:规则|政策|规定|要求|标准)(?:是|为|如下)(.{5,200})"),
            Pattern.compile("(?:不支持|支持|允许|禁止|可以|必须|需要)(.{5,100})")
    );

    // ==================== LLM 提示模板 ====================

    private static final String EXTRACTION_PROMPT = """
            你是一个记忆提取助手。请分析以下对话内容，提取出值得长期记住的信息。
            需要提取三类记忆（type 字段必须使用 USER_PROFILE / FACT / SUMMARY 之一）：
            1. USER_PROFILE — 用户画像：用户身份、职业、偏好、习惯、称呼、角色等关于「用户自身」的信息
            2. FACT — 事实记忆：业务规则、政策、商品/订单等客观信息（不含用户个人偏好）
            3. SUMMARY — 对话摘要：对整个对话或关键话题的一句总结（仅输出一条）
            注意：
            - 涉及「我/用户」的身份、偏好、习惯时，必须标为 USER_PROFILE，不要标为 FACT
            - SUMMARY 最多一条，概括整段对话主题
            请以 JSON 格式输出，格式示例：
            {
              "memories": [
                {"type": "USER_PROFILE", "content": "用户是一名电商运营人员"},
                {"type": "FACT", "content": "退货需要在签收后7天内申请"},
                {"type": "SUMMARY", "content": "用户询问了退货政策"}
              ]
            }
            对话内容：
            %s
            JSON 输出：
            """;

    public MemoryExtractor(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 从对话文本中提取记忆（两阶段：规则 + LLM）
     *
     * @param conversationText 对话文本
     * @return 提取出的记忆条目列表（暂不含 embedding）
     */
    public List<ExtractedMemory> extract(String conversationText) {
        log.info("Extracting memories from conversation text ({} chars)", conversationText.length());

        // 阶段一：规则预过滤
        RuleExtractResult ruleResult = ruleBasedExtract(conversationText);
        log.debug("Rule-based extraction found {} profile + {} fact candidates",
                ruleResult.userProfileCandidates().size(), ruleResult.factCandidates().size());

        // 阶段二：LLM 精提取
        List<ExtractedMemory> llmMemories = llmBasedExtract(conversationText);
        log.debug("LLM extraction found {} memories", llmMemories.size());

        // 合并结果（去重：同类型 + 内容精确匹配）
        List<ExtractedMemory> merged = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (ExtractedMemory mem : llmMemories) {
            if (seenKeys.add(dedupKey(mem.getType(), mem.getContent()))) {
                merged.add(mem);
            }
        }

        // 规则结果中补充 LLM 遗漏的（画像与事实分类型写入）
        mergeRuleCandidates(merged, seenKeys, ruleResult.userProfileCandidates(), MemoryType.USER_PROFILE);
        mergeRuleCandidates(merged, seenKeys, ruleResult.factCandidates(), MemoryType.FACT);

        collapseSummaries(merged);

        log.info("Extracted total {} memories after merge", merged.size());
        return merged;
    }

    private record RuleExtractResult(Set<String> userProfileCandidates, Set<String> factCandidates) {}

    /**
     * 规则预提取：偏好/画像模式 → USER_PROFILE，事实模式 → FACT
     */
    private RuleExtractResult ruleBasedExtract(String text) {
        Set<String> profileResults = new LinkedHashSet<>();
        Set<String> factResults = new LinkedHashSet<>();

        for (Pattern pattern : PREFERENCE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String extracted = matcher.group(1).trim();
                if (extracted.length() > 3) {
                    profileResults.add(extracted);
                }
            }
        }

        for (Pattern pattern : FACT_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String extracted = matcher.group(1).trim();
                if (extracted.length() > 5) {
                    factResults.add(extracted);
                }
            }
        }

        return new RuleExtractResult(profileResults, factResults);
    }

    private void collapseSummaries(List<ExtractedMemory> merged) {
        List<ExtractedMemory> summaries = merged.stream()
                .filter(m -> m.getType() == MemoryType.SUMMARY)
                .toList();
        if (summaries.size() <= 1) {
            return;
        }
        ExtractedMemory best = summaries.get(summaries.size() - 1);
        merged.removeIf(m -> m.getType() == MemoryType.SUMMARY);
        merged.add(best);
    }

    private void mergeRuleCandidates(List<ExtractedMemory> merged, Set<String> seenKeys,
                                     Set<String> candidates, MemoryType type) {
        for (String candidate : candidates) {
            if (seenKeys.contains(dedupKey(type, candidate))) {
                continue;
            }
            // 在 mergeRuleCandidates 中增加语义相似度检查
            boolean isSimilar = merged.stream()
                    .filter(m -> m.getType() == type)
                    .anyMatch(m -> calculateSimilarity(m.getContent(), candidate) > 0.85);
            if (!isSimilar) {
                seenKeys.add(dedupKey(type, candidate));
                merged.add(new ExtractedMemory(type, candidate, 0.3));
            }
        }
    }
    private static double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        s1 = s1.trim().toLowerCase();
        s2 = s2.trim().toLowerCase();

        if (s1.isEmpty() && s2.isEmpty()) {
            return 1.0;
        }

        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        // 如果完全相同，直接返回
        if (s1.equals(s2)) {
            return 1.0;
        }

        // 计算编辑距离
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        int editDistance = dp[s1.length()][s2.length()];
        int maxLen = Math.max(s1.length(), s2.length());

        return 1.0 - (double) editDistance / maxLen;
    }

    private static String dedupKey(MemoryType type, String content) {
        return type.name() + "|" + content.trim();
    }

    static String unwrapJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        Matcher fenced = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE)
                .matcher(trimmed);
        if (fenced.find()) {
            return fenced.group(1).trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * LLM 精提取
     */
    @SuppressWarnings("unchecked")
    private List<ExtractedMemory> llmBasedExtract(String conversationText) {
        try {
            Prompt prompt = new Prompt(EXTRACTION_PROMPT.formatted(conversationText));

            ChatResponse response = chatModel.call(prompt);
            String content = unwrapJson(response.getResult().getOutput().getText());

            Map<String, Object> result = ModelOptionsUtils.jsonToMap(content);
            List<Map<String, Object>> memoriesList = (List<Map<String, Object>>) result.get("memories");

            if (memoriesList == null) return List.of();

            List<ExtractedMemory> memories = new ArrayList<>();
            for (Map<String, Object> mem : memoriesList) {
                String typeStr = (String) mem.get("type");
                String contentStr = (String) mem.get("content");
                if (typeStr != null && contentStr != null && !contentStr.isBlank()) {
                    try {
                        MemoryType type = MemoryType.parse(typeStr);
                        memories.add(new ExtractedMemory(type, contentStr, 0.7));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown memory type from LLM: {}", typeStr);
                    }
                }
            }
            return memories;

        } catch (Exception e) {
            log.warn("LLM extraction failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== 内部类型 ====================

    /**
     * 提取出的记忆（尚未生成 embedding）
     */
    public static class ExtractedMemory {
        private final MemoryType type;
        private final String content;
        private final double confidence;

        public ExtractedMemory(MemoryType type, String content, double confidence) {
            this.type = type;
            this.content = content;
            this.confidence = confidence;
        }

        public MemoryType getType() { return type; }
        public String getContent() { return content; }
        public double getConfidence() { return confidence; }

        @Override
        public String toString() {
            return "[" + type + "] " + content + " (confidence=" + confidence + ")";
        }
    }
}

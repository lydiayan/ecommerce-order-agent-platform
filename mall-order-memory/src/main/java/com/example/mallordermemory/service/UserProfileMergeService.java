package com.example.mallordermemory.service;

import com.example.mallordermemory.profile.UserProfile;
import com.example.mallordermemory.profile.UserProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将会话增量合并为 MySQL 结构化用户画像（每用户一条，version 递增）。
 */
public class UserProfileMergeService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileMergeService.class);

    private static final String MERGE_PROMPT = """
            你是用户画像合并助手。请根据「现有画像」和「新增对话/线索」，输出合并后的完整用户画像。
            规则：
            1. 只输出 JSON，不要 markdown
            2. 新对话中的信息优先；与旧画像冲突时，以新对话为准
            3. 没有把握的信息保持 null，不要臆造
            4. profile_json 为对象，可包含 interests/skills/goals/preferences 等数组或键值
            5. confidence 为 0~1，信息越多、越一致则越高
            JSON 字段：
            occupation, department, city, language, response_style, profile_json, confidence
            现有画像：
            %s
            新增对话与画像线索：
            %s
            JSON：
            """;

    private final UserProfileRepository userProfileRepository;
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;

    public UserProfileMergeService(UserProfileRepository userProfileRepository,
                                   OpenAiChatModel chatModel,
                                   ObjectMapper objectMapper) {
        this.userProfileRepository = userProfileRepository;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 让模型根据增量对话和已有画像生成完整画像，并以版本递增方式写入 MySQL。
     * 模型输出无效或调用失败时记录日志并保持原画像不变。
     *
     * @param userId 用户编号
     * @param sessionId 本次画像来源的会话编号
     * @param conversationText 增量对话文本
     * @param profileHints 规则抽取出的画像线索
     * @return 实际写入 MySQL 时返回 {@code true}
     */
    public boolean mergeFromConversation(String userId,
                                         String sessionId,
                                         String conversationText,
                                         List<String> profileHints) {
        if (userId == null || userId.isBlank() || conversationText == null || conversationText.isBlank()) {
            return false;
        }
        Optional<UserProfile> existingOpt = userProfileRepository.findByUserId(userId);
        String existingJson = existingOpt.map(this::toExistingSnapshot).orElse("{}");
        String hintsText = buildHintsText(conversationText, profileHints);

        try {
            Prompt prompt = new Prompt(MERGE_PROMPT.formatted(existingJson, hintsText));
            ChatResponse response = chatModel.call(prompt);
            String raw = MemoryExtractor.unwrapJson(response.getResult().getOutput().getText());
            Map<String, Object> merged = ModelOptionsUtils.jsonToMap(raw);
            if (merged.isEmpty()) {
                log.info("User profile merge returned empty");
                return false;
            }

            UserProfile profile = existingOpt.orElseGet(UserProfile::new);
            profile.setUserId(userId);
            applyMerge(profile, merged);
            profile.setLastConversationId(sessionId);
            profile.setSource("conversation");

            UserProfile saved = userProfileRepository.upsert(profile);
            log.info("Merged user profile, version={}, confidence={}",
                    saved.getVersion(), saved.getConfidence());
            return true;
        } catch (Exception e) {
            log.warn("User profile merge failed: {}", e.getMessage());
            return false;
        }
    }

    private void applyMerge(UserProfile profile, Map<String, Object> merged) throws Exception {
        setIfPresent(merged, "occupation", profile::setOccupation);
        setIfPresent(merged, "department", profile::setDepartment);
        setIfPresent(merged, "city", profile::setCity);
        setIfPresent(merged, "language", profile::setLanguage);
        setIfPresent(merged, "response_style", profile::setResponseStyle);

        Object profileJson = merged.get("profile_json");
        if (profileJson != null) {
            if (profileJson instanceof String str && !str.isBlank()) {
                profile.setProfileJson(str);
            } else {
                profile.setProfileJson(objectMapper.writeValueAsString(profileJson));
            }
        }

        Object confidence = merged.get("confidence");
        if (confidence instanceof Number number) {
            BigDecimal value = BigDecimal.valueOf(number.doubleValue())
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.ONE)
                    .setScale(3, RoundingMode.HALF_UP);
            profile.setConfidence(value);
        } else if (profile.getConfidence() == null) {
            profile.setConfidence(new BigDecimal("0.500"));
        }
    }

    private String toExistingSnapshot(UserProfile profile) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "occupation", nullToEmpty(profile.getOccupation()),
                    "department", nullToEmpty(profile.getDepartment()),
                    "city", nullToEmpty(profile.getCity()),
                    "language", nullToEmpty(profile.getLanguage()),
                    "response_style", nullToEmpty(profile.getResponseStyle()),
                    "profile_json", parseProfileJsonNode(profile.getProfileJson()),
                    "confidence", profile.getConfidence() != null ? profile.getConfidence() : "0.5",
                    "version", profile.getVersion()
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode parseProfileJsonNode(String profileJson) {
        if (profileJson == null || profileJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(profileJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String buildHintsText(String conversationText, List<String> profileHints) {
        StringBuilder sb = new StringBuilder(conversationText.trim());
        if (profileHints != null && !profileHints.isEmpty()) {
            sb.append("\n\n画像线索:\n");
            for (String hint : profileHints) {
                sb.append("- ").append(hint).append('\n');
            }
        }
        return sb.toString();
    }

    private static void setIfPresent(Map<String, Object> merged, String key, java.util.function.Consumer<String> setter) {
        Object value = merged.get(key);
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (!text.isBlank() && !"null".equalsIgnoreCase(text)) {
            setter.accept(text);
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}

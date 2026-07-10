package com.example.mallordermemory.service;

import com.example.mallordermemory.profile.UserProfile;
import com.example.mallordermemory.profile.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 用户画像读取与 Prompt 格式化。
 */
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public Optional<UserProfile> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return userProfileRepository.findByUserId(userId.trim());
    }

    public String formatForPrompt(String userId) {
        return findByUserId(userId)
                .map(this::formatProfile)
                .orElse("");
    }

    public String formatProfile(UserProfile profile) {
        if (profile == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("--- 用户画像 ---\n");
        sb.append("版本: v").append(profile.getVersion());
        if (profile.getConfidence() != null) {
            sb.append("，可信度: ").append(profile.getConfidence());
        }
        sb.append('\n');
        appendLine(sb, "职业", profile.getOccupation());
        appendLine(sb, "部门", profile.getDepartment());
        appendLine(sb, "城市", profile.getCity());
        appendLine(sb, "语言", profile.getLanguage());
        appendLine(sb, "回复风格", profile.getResponseStyle());
        if (profile.getProfileJson() != null && !profile.getProfileJson().isBlank()) {
            sb.append("扩展画像: ").append(profile.getProfileJson().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value.trim()).append('\n');
        }
    }
}

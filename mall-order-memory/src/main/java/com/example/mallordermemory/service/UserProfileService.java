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

    /**
     * 按用户编号读取动态画像，空编号不会访问数据库。
     *
     * @param userId 用户编号
     * @return 用户画像；参数为空或记录不存在时为空
     */
    public Optional<UserProfile> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return userProfileRepository.findByUserId(userId.trim());
    }

    /**
     * 读取用户画像并格式化为可注入模型提示词的上下文。
     *
     * @param userId 用户编号
     * @return 画像提示词；无画像时返回空字符串
     */
    public String formatForPrompt(String userId) {
        return findByUserId(userId)
                .map(this::formatProfile)
                .orElse("");
    }

    /**
     * 将结构化画像格式化为包含版本和可信度的提示词片段。
     *
     * @param profile 用户画像
     * @return 提示词片段；画像为空时返回空字符串
     */
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

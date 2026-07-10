package com.example.mallordermemory.memory;

/**
 * 记忆类型枚举
 * <p>定义三种层次的记忆：</p>
 * <ul>
 *   <li><b>USER_PROFILE</b> — 用户画像，如偏好、角色、习惯等长期稳定的信息</li>
 *   <li><b>FACT</b> — 事实性记忆，如业务规则、实体属性等客观信息</li>
 *   <li><b>SUMMARY</b> — 对话摘要，对一段对话的高层概括</li>
 * </ul>
 */
public enum MemoryType {

    USER_PROFILE("user_profile", "用户画像"),
    FACT("fact", "事实记忆"),
    SUMMARY("summary", "对话摘要");

    private final String code;
    private final String displayName;

    MemoryType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 返回对应的 Milvus 集合名称
     */
    public String collectionName() {
        return "memory_" + code;
    }

    public static MemoryType fromCode(String code) {
        for (MemoryType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown memory type code: " + code);
    }

    /**
     * 兼容 LLM 可能返回的多种 type 格式：枚举名、code、中文别名等。
     */
    public static MemoryType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Memory type is blank");
        }
        String trimmed = raw.trim();
        String enumLike = trimmed.toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return valueOf(enumLike);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }

        String codeLike = trimmed.toLowerCase().replace('-', '_').replace(' ', '_');
        for (MemoryType type : values()) {
            if (type.code.equals(codeLike)) {
                return type;
            }
        }

        return switch (trimmed) {
            case "用户画像", "画像" -> USER_PROFILE;
            case "事实记忆", "事实" -> FACT;
            case "对话摘要", "摘要" -> SUMMARY;
            default -> throw new IllegalArgumentException("Unknown memory type: " + raw);
        };
    }
}

package com.css.mallorderagent.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class ApiTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApiTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 幂等写入预置 API Token；同名但哈希不同的旧 Token 会被撤销。
     * 数据库只保存哈希和用于识别的短前缀。
     *
     * @param name Token 名称
     * @param rawToken 原始 Token；为空时不执行写入
     * @param scopes 逗号分隔的权限范围
     */
    public void seed(String name, String rawToken, String scopes) {
        if (rawToken == null || rawToken.isBlank()) return;
        String hash = TokenHash.sha256(rawToken.trim());
        String prefix = rawToken.trim().substring(0, Math.min(12, rawToken.trim().length()));
        jdbcTemplate.update("""
                UPDATE api_token SET enabled = FALSE, revoked_at = CURRENT_TIMESTAMP
                WHERE token_name = ? AND token_hash <> ? AND revoked_at IS NULL
                """, name, hash);
        jdbcTemplate.update("""
                INSERT INTO api_token(token_name, token_prefix, token_hash, scopes)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE token_name = VALUES(token_name), scopes = VALUES(scopes),
                    enabled = TRUE, revoked_at = NULL
                """, name, prefix, hash, scopes);
    }

    /**
     * 校验原始 Token 对应的启用、未撤销且未过期记录，并更新最近使用时间。
     *
     * @param rawToken 请求携带的原始 Token
     * @return 有效 Token 及解析后的权限范围；无效时为空
     */
    public Optional<TokenRecord> findActive(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String hash = TokenHash.sha256(rawToken.trim());
        List<TokenRecord> records = jdbcTemplate.query("""
                SELECT id, token_name, scopes, expires_at
                FROM api_token
                WHERE token_hash = ? AND enabled = TRUE AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """, (rs, rowNum) -> new TokenRecord(
                rs.getLong("id"), rs.getString("token_name"),
                Arrays.stream(rs.getString("scopes").split(","))
                        .map(String::trim).filter(value -> !value.isBlank()).toList(),
                rs.getTimestamp("expires_at") != null
                        ? rs.getTimestamp("expires_at").toLocalDateTime() : null), hash);
        records.stream().findFirst().ifPresent(record -> jdbcTemplate.update(
                "UPDATE api_token SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", record.id()));
        return records.stream().findFirst();
    }

    /**
     * 创建新的 API Token 记录，仅持久化哈希和短前缀。
     *
     * @param name Token 名称
     * @param rawToken 待哈希的原始 Token
     * @param scopes 逗号分隔的权限范围
     * @param expiresAt 可选过期时间
     * @return 新记录主键，无法读取时返回 {@code -1}
     */
    public long create(String name, String rawToken, String scopes, LocalDateTime expiresAt) {
        String normalized = rawToken.trim();
        jdbcTemplate.update("""
                INSERT INTO api_token(token_name, token_prefix, token_hash, scopes, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, name, normalized.substring(0, Math.min(12, normalized.length())),
                TokenHash.sha256(normalized), scopes,
                expiresAt != null ? Timestamp.valueOf(expiresAt) : null);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM api_token WHERE token_hash = ?", Long.class, TokenHash.sha256(normalized));
        return id != null ? id : -1;
    }

    /**
     * 查询所有 API Token 的非敏感摘要，不返回原始 Token 或完整哈希。
     *
     * @return 按创建时间倒序排列的 Token 摘要
     */
    public List<TokenSummary> findAll() {
        return jdbcTemplate.query("""
                SELECT id, token_name, token_prefix, scopes, enabled, expires_at,
                       last_used_at, created_at, revoked_at
                FROM api_token ORDER BY created_at DESC
                """, (rs, rowNum) -> new TokenSummary(
                rs.getLong("id"), rs.getString("token_name"), rs.getString("token_prefix"),
                rs.getString("scopes"), rs.getBoolean("enabled"),
                toLocalDateTime(rs.getTimestamp("expires_at")),
                toLocalDateTime(rs.getTimestamp("last_used_at")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("revoked_at"))));
    }

    /**
     * 撤销尚未撤销的 API Token。
     *
     * @param tokenId Token 主键
     * @return 本次实际撤销一条记录时返回 {@code true}
     */
    public boolean revoke(long tokenId) {
        return jdbcTemplate.update("""
                UPDATE api_token SET enabled = FALSE, revoked_at = CURRENT_TIMESTAMP
                WHERE id = ? AND revoked_at IS NULL
                """, tokenId) == 1;
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    public record TokenRecord(long id, String name, List<String> scopes, LocalDateTime expiresAt) { }

    public record TokenSummary(long id, String name, String prefix, String scopes, boolean enabled,
                               LocalDateTime expiresAt, LocalDateTime lastUsedAt,
                               LocalDateTime createdAt, LocalDateTime revokedAt) { }
}

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

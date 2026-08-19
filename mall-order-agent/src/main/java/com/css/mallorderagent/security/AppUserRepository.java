package com.css.mallorderagent.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class AppUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public AppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserRow> findByUsername(String username) {
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT u.id, u.username, u.password_hash, u.actor_user_id, u.enabled,
                       u.password_change_required, u.failed_login_count, u.locked_until,
                       u.auth_version, i.display_name
                FROM app_user u
                LEFT JOIN actor_identity i ON i.actor_user_id = u.actor_user_id
                WHERE LOWER(u.username) = LOWER(?)
                """, (rs, rowNum) -> new UserRow(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("actor_user_id"), rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getBoolean("password_change_required"), rs.getInt("failed_login_count"),
                rs.getTimestamp("locked_until") != null
                        ? rs.getTimestamp("locked_until").toLocalDateTime() : null,
                rs.getLong("auth_version")), username.trim());
        return rows.stream().findFirst();
    }

    public Optional<UserRow> findById(long userId) {
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT u.id, u.username, u.password_hash, u.actor_user_id, u.enabled,
                       u.password_change_required, u.failed_login_count, u.locked_until,
                       u.auth_version, i.display_name
                FROM app_user u
                LEFT JOIN actor_identity i ON i.actor_user_id = u.actor_user_id
                WHERE u.id = ?
                """, (rs, rowNum) -> new UserRow(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("actor_user_id"), rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getBoolean("password_change_required"), rs.getInt("failed_login_count"),
                rs.getTimestamp("locked_until") != null
                        ? rs.getTimestamp("locked_until").toLocalDateTime() : null,
                rs.getLong("auth_version")), userId);
        return rows.stream().findFirst();
    }

    public Set<String> findRoles(long userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT role_code FROM app_user_role WHERE user_id = ? ORDER BY role_code
                """, String.class, userId));
    }

    public List<String> findRoleCapabilities(long userId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT rc.capability
                FROM app_user_role ur
                JOIN role_capability rc ON rc.role_code = ur.role_code
                WHERE ur.user_id = ?
                ORDER BY rc.capability
                """, String.class, userId);
    }

    public List<String> findActorCapabilities(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) return List.of();
        return jdbcTemplate.queryForList("""
                SELECT capability FROM actor_capability
                WHERE actor_user_id = ? ORDER BY capability
                """, String.class, actorUserId);
    }

    public long currentAuthVersion(long userId) {
        Long version = jdbcTemplate.queryForObject(
                "SELECT auth_version FROM app_user WHERE id = ?", Long.class, userId);
        return version != null ? version : -1;
    }

    public void recordLoginSuccess(long userId) {
        jdbcTemplate.update("""
                UPDATE app_user SET failed_login_count = 0, locked_until = NULL,
                    last_login_at = CURRENT_TIMESTAMP WHERE id = ?
                """, userId);
    }

    public int recordLoginFailure(String username, int maxFailures, long lockSeconds) {
        jdbcTemplate.update("""
                UPDATE app_user
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE WHEN failed_login_count + 1 >= ?
                        THEN DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND) ELSE locked_until END
                WHERE LOWER(username) = LOWER(?)
                """, maxFailures, lockSeconds, username);
        return findByUsername(username).map(UserRow::failedLoginCount).orElse(0);
    }

    public void changePassword(long userId, String passwordHash, boolean requireChange) {
        jdbcTemplate.update("""
                UPDATE app_user SET password_hash = ?, password_change_required = ?,
                    password_changed_at = CURRENT_TIMESTAMP, failed_login_count = 0,
                    locked_until = NULL, auth_version = auth_version + 1 WHERE id = ?
                """, passwordHash, requireChange, userId);
    }

    public void seedUser(String username, String passwordHash, String actorUserId,
                         String roleCode, boolean passwordChangeRequired) {
        jdbcTemplate.update("""
                INSERT INTO app_user(username, password_hash, actor_user_id, password_change_required)
                SELECT ?, ?, ?, ? WHERE NOT EXISTS (
                    SELECT 1 FROM app_user WHERE LOWER(username) = LOWER(?)
                )
                """, username, passwordHash, actorUserId, passwordChangeRequired, username);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE LOWER(username) = LOWER(?)", Long.class, username);
        jdbcTemplate.update("INSERT IGNORE INTO app_user_role(user_id, role_code) VALUES (?, ?)",
                userId, roleCode);
    }

    @Transactional
    public long createUser(String username, String passwordHash, String actorUserId, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO app_user(username, password_hash, actor_user_id, password_change_required)
                VALUES (?, ?, ?, TRUE)
                """, username, passwordHash, actorUserId);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE LOWER(username) = LOWER(?)", Long.class, username);
        jdbcTemplate.update("INSERT INTO app_user_role(user_id, role_code) VALUES (?, ?)", userId, roleCode);
        return userId;
    }

    public List<UserSummary> findAll() {
        return jdbcTemplate.query("""
                SELECT u.id, u.username, u.actor_user_id, i.display_name, u.enabled,
                       u.password_change_required, u.failed_login_count, u.locked_until,
                       u.last_login_at, u.updated_at
                FROM app_user u LEFT JOIN actor_identity i ON i.actor_user_id = u.actor_user_id
                ORDER BY u.username
                """, (rs, rowNum) -> new UserSummary(
                rs.getLong("id"), rs.getString("username"), rs.getString("actor_user_id"),
                rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getBoolean("password_change_required"), rs.getInt("failed_login_count"),
                toLocalDateTime(rs.getTimestamp("locked_until")),
                toLocalDateTime(rs.getTimestamp("last_login_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")), findRoles(rs.getLong("id"))));
    }

    public void setEnabled(long userId, boolean enabled) {
        jdbcTemplate.update("UPDATE app_user SET enabled = ?, auth_version = auth_version + 1 WHERE id = ?",
                enabled, userId);
    }

    public void unlock(long userId) {
        jdbcTemplate.update("""
                UPDATE app_user SET failed_login_count = 0, locked_until = NULL,
                    auth_version = auth_version + 1 WHERE id = ?
                """, userId);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    public record UserRow(long id, String username, String passwordHash, String actorUserId,
                          String displayName, boolean enabled, boolean passwordChangeRequired,
                          int failedLoginCount, LocalDateTime lockedUntil, long authVersion) {
        public boolean accountNonLocked() {
            return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
        }
    }

    public record UserSummary(long id, String username, String actorUserId, String displayName,
                              boolean enabled, boolean passwordChangeRequired, int failedLoginCount,
                              LocalDateTime lockedUntil, LocalDateTime lastLoginAt,
                              LocalDateTime updatedAt, Set<String> roles) {
    }
}

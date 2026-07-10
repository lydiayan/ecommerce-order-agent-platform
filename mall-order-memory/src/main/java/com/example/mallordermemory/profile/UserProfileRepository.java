package com.example.mallordermemory.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * user_profile 表 JDBC 访问。
 */
public class UserProfileRepository {

    private static final RowMapper<UserProfile> ROW_MAPPER = (rs, rowNum) -> {
        UserProfile profile = new UserProfile();
        profile.setId(rs.getLong("id"));
        profile.setUserId(rs.getString("user_id"));
        profile.setOccupation(rs.getString("occupation"));
        profile.setDepartment(rs.getString("department"));
        profile.setCity(rs.getString("city"));
        profile.setLanguage(rs.getString("language"));
        profile.setResponseStyle(rs.getString("response_style"));
        profile.setProfileJson(rs.getString("profile_json"));
        profile.setConfidence(rs.getBigDecimal("confidence"));
        profile.setVersion(rs.getInt("version"));
        profile.setSource(rs.getString("source"));
        profile.setLastConversationId(rs.getString("last_conversation_id"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            profile.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            profile.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        return profile;
    };

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> findByUserId(String userId) {
        List<UserProfile> rows = jdbcTemplate.query(
                """
                        SELECT id, user_id, occupation, department, city, language, response_style,
                               profile_json, confidence, version, source, last_conversation_id,
                               created_at, updated_at
                        FROM user_profile
                        WHERE user_id = ?
                        LIMIT 1
                        """,
                ROW_MAPPER,
                userId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public UserProfile upsert(UserProfile profile) {
        Optional<UserProfile> existing = findByUserId(profile.getUserId());
        if (existing.isEmpty()) {
            return insert(profile);
        }
        UserProfile current = existing.get();
        profile.setId(current.getId());
        profile.setVersion(current.getVersion() + 1);
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(current.getCreatedAt());
        }
        jdbcTemplate.update(
                """
                        UPDATE user_profile SET
                            occupation = ?, department = ?, city = ?, language = ?,
                            response_style = ?, profile_json = CAST(? AS JSON),
                            confidence = ?, version = ?, source = ?, last_conversation_id = ?
                        WHERE user_id = ?
                        """,
                profile.getOccupation(),
                profile.getDepartment(),
                profile.getCity(),
                profile.getLanguage(),
                profile.getResponseStyle(),
                profile.getProfileJson(),
                profile.getConfidence(),
                profile.getVersion(),
                profile.getSource(),
                profile.getLastConversationId(),
                profile.getUserId()
        );
        return findByUserId(profile.getUserId()).orElse(profile);
    }

    private UserProfile insert(UserProfile profile) {
        if (profile.getVersion() <= 0) {
            profile.setVersion(1);
        }
        if (profile.getConfidence() == null) {
            profile.setConfidence(new BigDecimal("0.500"));
        }
        if (profile.getLanguage() == null || profile.getLanguage().isBlank()) {
            profile.setLanguage("zh-CN");
        }
        if (profile.getSource() == null || profile.getSource().isBlank()) {
            profile.setSource("conversation");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                            INSERT INTO user_profile (
                                user_id, occupation, department, city, language, response_style,
                                profile_json, confidence, version, source, last_conversation_id, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, profile.getUserId());
            ps.setString(2, profile.getOccupation());
            ps.setString(3, profile.getDepartment());
            ps.setString(4, profile.getCity());
            ps.setString(5, profile.getLanguage());
            ps.setString(6, profile.getResponseStyle());
            ps.setString(7, profile.getProfileJson());
            ps.setBigDecimal(8, profile.getConfidence());
            ps.setInt(9, profile.getVersion());
            ps.setString(10, profile.getSource());
            ps.setString(11, profile.getLastConversationId());
            ps.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            profile.setId(key.longValue());
        }
        return profile;
    }
}

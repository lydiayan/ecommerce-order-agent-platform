package com.css.mallorderagent.skill.registry;

import com.css.mallorderagent.skill.model.ActiveSkill;
import com.css.mallorderagent.skill.model.DownloadedSkillPackage;
import com.css.mallorderagent.skill.model.SkillManifest;
import com.css.mallorderagent.skill.model.SkillVersionView;
import com.css.mallorderagent.skill.model.SkillView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SkillRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SkillRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SkillVersionView install(SkillManifest manifest, DownloadedSkillPackage source,
                                    String storageKey, String installedBy) {
        long skillId = findOrCreateSkill(manifest, installedBy);
        jdbcTemplate.update("""
                INSERT IGNORE INTO skill_policy(skill_id, allow_implicit) VALUES (?, ?)
                """, skillId, manifest.allowImplicitInvocation());

        Optional<SkillVersionView> existing = findVersionByDigest(skillId, source.digest());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO skill_version(
                            skill_id, package_digest, description, source_url, source_ref, source_path,
                            storage_key, instructions, required_actions, trigger_keywords, created_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, skillId);
                statement.setString(2, source.digest());
                statement.setString(3, manifest.description());
                statement.setString(4, source.sourceUrl());
                statement.setString(5, source.sourceRef());
                statement.setString(6, source.sourcePath());
                statement.setString(7, storageKey);
                statement.setString(8, manifest.instructions());
                statement.setString(9, writeList(manifest.requiredActions()));
                statement.setString(10, writeList(manifest.triggerKeywords()));
                statement.setString(11, installedBy);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("skill version insert returned no id");
            }
            return requireVersion(key.longValue());
        } catch (DuplicateKeyException e) {
            return findVersionByDigest(skillId, source.digest()).orElseThrow(() -> e);
        }
    }

    public List<SkillView> findAll() {
        return jdbcTemplate.query("""
                SELECT s.id, s.name, s.description, s.enabled, s.active_version_id,
                       p.allow_implicit, s.created_at, s.updated_at
                FROM skill s JOIN skill_policy p ON p.skill_id = s.id
                ORDER BY s.name
                """, (rs, rowNum) -> new SkillView(
                rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getBoolean("enabled"), nullableLong(rs.getObject("active_version_id")),
                rs.getBoolean("allow_implicit"), timestamp(rs, "created_at"), timestamp(rs, "updated_at")));
    }

    public List<SkillVersionView> findVersions(long skillId) {
        return jdbcTemplate.query("""
                SELECT id, skill_id, package_digest, source_url, source_ref, source_path,
                       storage_key, required_actions, trigger_keywords, created_by, created_at
                FROM skill_version WHERE skill_id = ? ORDER BY created_at DESC, id DESC
                """, this::mapVersion, skillId);
    }

    public List<ActiveSkill> findActive() {
        return jdbcTemplate.query("""
                SELECT s.id AS skill_id, v.id AS version_id, s.name, v.description,
                       v.package_digest, v.instructions, v.required_actions, v.trigger_keywords,
                       p.allow_implicit
                FROM skill s
                JOIN skill_version v ON v.id = s.active_version_id
                JOIN skill_policy p ON p.skill_id = s.id
                WHERE s.enabled = TRUE
                ORDER BY s.name
                """, (rs, rowNum) -> new ActiveSkill(
                rs.getLong("skill_id"), rs.getLong("version_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("package_digest"),
                rs.getString("instructions"), readList(rs.getString("required_actions")),
                readList(rs.getString("trigger_keywords")), rs.getBoolean("allow_implicit")));
    }

    public boolean activate(long skillId, long versionId) {
        return jdbcTemplate.update("""
                UPDATE skill SET active_version_id = ?, enabled = TRUE,
                    description = (SELECT description FROM skill_version WHERE id = ? AND skill_id = ?)
                WHERE id = ? AND EXISTS (
                    SELECT 1 FROM skill_version WHERE id = ? AND skill_id = ?
                )
                """, versionId, versionId, skillId, skillId, versionId, skillId) == 1;
    }

    public boolean disable(long skillId) {
        return jdbcTemplate.update("UPDATE skill SET enabled = FALSE WHERE id = ?", skillId) == 1;
    }

    public boolean updatePolicy(long skillId, boolean allowImplicit) {
        return jdbcTemplate.update("UPDATE skill_policy SET allow_implicit = ? WHERE skill_id = ?",
                allowImplicit, skillId) == 1;
    }

    private Optional<Long> findSkillId(String name) {
        return jdbcTemplate.query("SELECT id FROM skill WHERE name = ?",
                rs -> rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty(), name);
    }

    private long insertSkill(SkillManifest manifest, String installedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO skill(name, description, created_by) VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, manifest.name());
            statement.setString(2, manifest.description());
            statement.setString(3, installedBy);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("skill insert returned no id");
        }
        return key.longValue();
    }

    private long findOrCreateSkill(SkillManifest manifest, String installedBy) {
        Optional<Long> existing = findSkillId(manifest.name());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return insertSkill(manifest, installedBy);
        } catch (DuplicateKeyException e) {
            return findSkillId(manifest.name()).orElseThrow(() -> e);
        }
    }

    private Optional<SkillVersionView> findVersionByDigest(long skillId, String digest) {
        return jdbcTemplate.query("""
                SELECT id, skill_id, package_digest, source_url, source_ref, source_path,
                       storage_key, required_actions, trigger_keywords, created_by, created_at
                FROM skill_version WHERE skill_id = ? AND package_digest = ?
                """, rs -> rs.next() ? Optional.of(mapVersion(rs, 0)) : Optional.empty(), skillId, digest);
    }

    private SkillVersionView requireVersion(long versionId) {
        return jdbcTemplate.query("""
                SELECT id, skill_id, package_digest, source_url, source_ref, source_path,
                       storage_key, required_actions, trigger_keywords, created_by, created_at
                FROM skill_version WHERE id = ?
                """, rs -> {
            if (!rs.next()) {
                throw new IllegalStateException("installed skill version cannot be reloaded");
            }
            return mapVersion(rs, 0);
        }, versionId);
    }

    private SkillVersionView mapVersion(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new SkillVersionView(
                rs.getLong("id"), rs.getLong("skill_id"), rs.getString("package_digest"),
                rs.getString("source_url"), rs.getString("source_ref"), rs.getString("source_path"),
                rs.getString("storage_key"), readList(rs.getString("required_actions")),
                readList(rs.getString("trigger_keywords")), rs.getString("created_by"),
                timestamp(rs, "created_at"));
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("skill metadata cannot be serialized", e);
        }
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored skill metadata is invalid", e);
        }
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static LocalDateTime timestamp(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}

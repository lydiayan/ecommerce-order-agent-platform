package com.css.mallorderagent.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DemoPersonaRepository {

    private final JdbcTemplate jdbcTemplate;

    public DemoPersonaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PersonaRow> findAllActive() {
        return jdbcTemplate.query("""
                SELECT actor_user_id, category, display_name, job_title, department,
                       description, welcome_message
                FROM actor_identity
                WHERE active = TRUE
                ORDER BY sort_order, actor_user_id
                """, (rs, rowNum) -> new PersonaRow(
                rs.getString("actor_user_id"),
                DemoPersonaCategory.valueOf(rs.getString("category")),
                rs.getString("display_name"),
                rs.getString("job_title"),
                rs.getString("department"),
                rs.getString("description"),
                rs.getString("welcome_message")));
    }

    public Optional<PersonaRow> findActiveById(String actorUserId) {
        List<PersonaRow> rows = jdbcTemplate.query("""
                SELECT actor_user_id, category, display_name, job_title, department,
                       description, welcome_message
                FROM actor_identity
                WHERE actor_user_id = ? AND active = TRUE
                """, (rs, rowNum) -> new PersonaRow(
                rs.getString("actor_user_id"),
                DemoPersonaCategory.valueOf(rs.getString("category")),
                rs.getString("display_name"),
                rs.getString("job_title"),
                rs.getString("department"),
                rs.getString("description"),
                rs.getString("welcome_message")), actorUserId);
        return rows.stream().findFirst();
    }

    public List<String> findScopes(String actorUserId, String scopeType) {
        return jdbcTemplate.queryForList("""
                SELECT scope_value
                FROM actor_rag_scope
                WHERE actor_user_id = ? AND scope_type = ?
                ORDER BY scope_value
                """, String.class, actorUserId, scopeType);
    }

    public List<DemoCapability> findCapabilities(String actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT capability
                FROM actor_capability
                WHERE actor_user_id = ?
                ORDER BY capability
                """, String.class, actorUserId).stream()
                .map(DemoCapability::valueOf)
                .toList();
    }

    public List<String> findSuggestions(String actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT suggestion
                FROM actor_suggestion
                WHERE actor_user_id = ?
                ORDER BY sort_order
                """, String.class, actorUserId);
    }

    public List<String> findAssignedCustomerIds(String actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT customer_user_id
                FROM actor_customer_scope
                WHERE sales_actor_user_id = ?
                ORDER BY customer_user_id
                """, String.class, actorUserId);
    }

    public List<String> findAllActorUserIds() {
        return jdbcTemplate.queryForList(
                "SELECT actor_user_id FROM actor_identity ORDER BY actor_user_id", String.class);
    }

    public record PersonaRow(
            String actorUserId,
            DemoPersonaCategory category,
            String displayName,
            String jobTitle,
            String department,
            String description,
            String welcomeMessage) {
    }
}

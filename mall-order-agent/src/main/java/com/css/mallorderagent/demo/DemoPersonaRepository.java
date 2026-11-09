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

    /**
     * 查询全部启用的演示身份。
     *
     * @return 按展示顺序排列的身份记录
     */
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

    /**
     * 按业务身份编号查询启用的演示身份。
     *
     * @param actorUserId 业务身份编号
     * @return 身份记录，不存在或停用时为空
     */
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

    /**
     * 查询身份指定类型的 RAG 知识范围。
     *
     * @param actorUserId 业务身份编号
     * @param scopeType 范围类型，例如 {@code ROLE} 或 {@code DEPARTMENT}
     * @return 排序后的范围值
     */
    public List<String> findScopes(String actorUserId, String scopeType) {
        return jdbcTemplate.queryForList("""
                SELECT scope_value
                FROM actor_rag_scope
                WHERE actor_user_id = ? AND scope_type = ?
                ORDER BY scope_value
                """, String.class, actorUserId, scopeType);
    }

    /**
     * 查询身份获授的业务能力。
     *
     * @param actorUserId 业务身份编号
     * @return 能力枚举列表
     */
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

    /**
     * 查询身份对应的演示问题建议。
     *
     * @param actorUserId 业务身份编号
     * @return 按配置顺序排列的问题建议
     */
    public List<String> findSuggestions(String actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT suggestion
                FROM actor_suggestion
                WHERE actor_user_id = ?
                ORDER BY sort_order
                """, String.class, actorUserId);
    }

    /**
     * 查询销售身份被明确分配的客户范围。
     *
     * @param actorUserId 销售业务身份编号
     * @return 客户业务身份编号
     */
    public List<String> findAssignedCustomerIds(String actorUserId) {
        return jdbcTemplate.queryForList("""
                SELECT customer_user_id
                FROM actor_customer_scope
                WHERE sales_actor_user_id = ?
                ORDER BY customer_user_id
                """, String.class, actorUserId);
    }

    /**
     * 查询全部演示身份编号，包括当前停用身份。
     *
     * @return 排序后的身份编号
     */
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

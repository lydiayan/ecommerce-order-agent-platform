package com.css.mallorderagent.security;

import com.example.mallorderobservability.trace.TracePrivacy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private final JdbcTemplate jdbcTemplate;
    private final ClientIpResolver clientIpResolver;

    public SecurityAuditService(JdbcTemplate jdbcTemplate, ClientIpResolver clientIpResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 记录安全事件。主体和资源只保存不可逆指纹，审计写入失败会记录错误日志，
     * 但不会中断原业务请求。
     *
     * @param eventType 事件类型
     * @param subject 操作主体原始标识，入库前会被指纹化
     * @param resource 目标资源原始标识，入库前会被指纹化
     * @param outcome 操作结果
     * @param details 补充信息，最多保存 1000 个字符
     * @param request 当前 HTTP 请求，可为空
     */
    public void record(String eventType, String subject, String resource, String outcome,
                       String details, HttpServletRequest request) {
        String userAgent = request != null ? truncate(request.getHeader("User-Agent"), 255) : null;
        String sourceIp = request != null ? clientIpResolver.resolve(request) : null;
        try {
            jdbcTemplate.update("""
                    INSERT INTO security_audit_event(
                        event_type, subject_fingerprint, resource_fingerprint, outcome,
                        source_ip, user_agent, details)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, eventType, fingerprint(subject), fingerprint(resource), outcome,
                    sourceIp, userAgent, truncate(details, 1000));
        } catch (RuntimeException e) {
            log.error("Security audit write failed, eventType={}, outcome={}", eventType, outcome, e);
        }
    }

    /**
     * 按时间倒序查询最近的安全事件。
     *
     * @param requestedLimit 请求条数，实际限制在 1 至 200
     * @return 已脱敏的审计事件列表
     */
    public List<AuditEventView> latest(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return jdbcTemplate.query("""
                SELECT id, event_type, subject_fingerprint, resource_fingerprint, outcome,
                       source_ip, user_agent, details, created_at
                FROM security_audit_event ORDER BY created_at DESC LIMIT ?
                """, (rs, rowNum) -> new AuditEventView(
                rs.getLong("id"), rs.getString("event_type"), rs.getString("subject_fingerprint"),
                rs.getString("resource_fingerprint"), rs.getString("outcome"),
                rs.getString("source_ip"), rs.getString("user_agent"), rs.getString("details"),
                toLocalDateTime(rs.getTimestamp("created_at"))), limit);
    }

    /**
     * 每日分批删除超过 180 天的安全审计事件，避免长事务和大批量锁定。
     */
    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Shanghai")
    public void purgeExpiredEvents() {
        int total = 0;
        int deleted;
        do {
            deleted = jdbcTemplate.update("""
                    DELETE FROM security_audit_event
                    WHERE created_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY)
                    LIMIT 1000
                    """);
            total += deleted;
        } while (deleted == 1000);
        if (total > 0) log.info("Purged {} expired security audit events", total);
    }

    private static String fingerprint(String value) {
        return value == null || value.isBlank() ? null : TracePrivacy.fingerprint(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    public record AuditEventView(long id, String eventType, String subjectFingerprint,
                                 String resourceFingerprint, String outcome, String sourceIp,
                                 String userAgent, String details, LocalDateTime createdAt) { }
}

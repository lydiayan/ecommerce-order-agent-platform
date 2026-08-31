package com.css.mallorderagent.knowledge;

import com.example.mallordermilvusrag.dto.DocumentMetadata;
import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;
import com.example.mallordermilvusrag.dto.KnowledgeImportStatus;
import com.example.mallordermilvusrag.service.KnowledgeDocumentStore;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class MysqlKnowledgeDocumentStore implements KnowledgeDocumentStore {

    private static final String SELECT_COLUMNS = """
            SELECT filename, document_id, split_strategy, department, visible_role,
                   document_version, content_type, chunk_count, average_token_count,
                   max_token_count, overlap_token_count, original_file_size, file_sha256,
                   import_status, last_error, imported_at, updated_at
            FROM knowledge_document
            """;

    private static final RowMapper<KnowledgeDocumentRecord> ROW_MAPPER = (rs, rowNum) ->
            new KnowledgeDocumentRecord(
                    rs.getString("filename"),
                    new DocumentMetadata(rs.getString("filename"), rs.getString("department"),
                            rs.getString("visible_role"), rs.getString("document_version"),
                            dateText(rs.getTimestamp("imported_at"))),
                    rs.getString("document_id"), rs.getString("split_strategy"),
                    rs.getString("content_type"), rs.getInt("chunk_count"),
                    rs.getInt("average_token_count"), rs.getInt("max_token_count"),
                    rs.getInt("overlap_token_count"), rs.getLong("original_file_size"),
                    rs.getString("file_sha256"),
                    KnowledgeImportStatus.valueOf(rs.getString("import_status")),
                    rs.getString("last_error"), toLocalDateTime(rs.getTimestamp("imported_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public MysqlKnowledgeDocumentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询全部知识文档导入状态，供知识库目录与 Milvus 实际分块合并展示。
     *
     * @return 按文件名排序的导入记录
     */
    @Override
    public List<KnowledgeDocumentRecord> findAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY filename", ROW_MAPPER);
    }

    /**
     * 按文件名查询知识文档导入状态。
     *
     * @param filename 文件名
     * @return 导入记录，不存在时为空
     */
    @Override
    public Optional<KnowledgeDocumentRecord> findByFilename(String filename) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE filename = ?", ROW_MAPPER, filename)
                .stream().findFirst();
    }

    /**
     * 新建或覆盖文档元数据并标记为导入中，同时清除旧失败信息和完成时间。
     *
     * @param record 当前导入的文档及切分统计
     */
    @Override
    public void saveImporting(KnowledgeDocumentRecord record) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_document(
                    filename, document_id, split_strategy, department, visible_role,
                    document_version, content_type, chunk_count, average_token_count,
                    max_token_count, overlap_token_count, original_file_size, file_sha256,
                    import_status, last_error, imported_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'IMPORTING', NULL, NULL)
                ON DUPLICATE KEY UPDATE
                    document_id = VALUES(document_id), split_strategy = VALUES(split_strategy),
                    department = VALUES(department), visible_role = VALUES(visible_role),
                    document_version = VALUES(document_version), content_type = VALUES(content_type),
                    chunk_count = VALUES(chunk_count), average_token_count = VALUES(average_token_count),
                    max_token_count = VALUES(max_token_count), overlap_token_count = VALUES(overlap_token_count),
                    original_file_size = VALUES(original_file_size), file_sha256 = VALUES(file_sha256),
                    import_status = 'IMPORTING', last_error = NULL, imported_at = NULL
                """, values(record));
    }

    /**
     * 在向量分块全部写入后将已初始化的导入记录标记为成功。
     *
     * @param record 已成功导入的文档记录
     */
    @Override
    public void saveReady(KnowledgeDocumentRecord record) {
        int updated = jdbcTemplate.update("""
                UPDATE knowledge_document
                SET document_id = ?, split_strategy = ?, department = ?, visible_role = ?,
                    document_version = ?, content_type = ?, chunk_count = ?,
                    average_token_count = ?, max_token_count = ?, overlap_token_count = ?,
                    original_file_size = ?, file_sha256 = ?, import_status = 'READY',
                    last_error = NULL, imported_at = ?
                WHERE filename = ?
                """, record.documentId(), record.strategy(), record.metadata().getDepartment(),
                record.metadata().getRole(), record.metadata().getVersion(), record.contentType(),
                record.chunkCount(), record.averageTokenCount(), record.maxTokenCount(),
                record.overlapTokenCount(), record.originalFileSize(), record.fileSha256(),
                Timestamp.valueOf(record.importedAt()), record.filename());
        if (updated != 1) {
            throw new IllegalStateException("Knowledge document import state was not initialized");
        }
    }

    /**
     * 将文档标记为导入失败，并把错误信息限制在 1000 个字符内。
     *
     * @param filename 文件名
     * @param errorMessage 失败原因
     */
    @Override
    public void saveFailed(String filename, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE knowledge_document
                SET import_status = 'FAILED', last_error = ?, imported_at = NULL
                WHERE filename = ?
                """, truncate(errorMessage, 1000), filename);
    }

    private static Object[] values(KnowledgeDocumentRecord record) {
        return new Object[]{record.filename(), record.documentId(), record.strategy(),
                record.metadata().getDepartment(), record.metadata().getRole(),
                record.metadata().getVersion(), record.contentType(), record.chunkCount(),
                record.averageTokenCount(), record.maxTokenCount(), record.overlapTokenCount(),
                record.originalFileSize(), record.fileSha256()};
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value != null ? value.toLocalDateTime() : null;
    }

    private static String dateText(Timestamp value) {
        return value != null ? value.toLocalDateTime().toLocalDate().toString() : null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

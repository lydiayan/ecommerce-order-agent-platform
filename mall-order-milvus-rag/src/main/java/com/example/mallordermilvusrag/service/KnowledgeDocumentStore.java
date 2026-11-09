package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.dto.KnowledgeDocumentRecord;

import java.util.List;
import java.util.Optional;

/** 知识文档导入状态的持久化边界，由宿主 Agent 应用提供实现。 */
public interface KnowledgeDocumentStore {

    /** @return 全部知识文档导入记录 */
    List<KnowledgeDocumentRecord> findAll();

    /**
     * @param filename 文件名
     * @return 对应导入记录，不存在时为空
     */
    Optional<KnowledgeDocumentRecord> findByFilename(String filename);

    /** @param record 进入导入中的文档记录 */
    void saveImporting(KnowledgeDocumentRecord record);

    /** @param record 已成功完成向量写入的文档记录 */
    void saveReady(KnowledgeDocumentRecord record);

    /**
     * @param filename 导入失败的文件名
     * @param errorMessage 截断或清洗后的错误信息
     */
    void saveFailed(String filename, String errorMessage);
}

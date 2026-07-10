package com.example.mallordermilvusrag.service;

import com.example.mallordermilvusrag.config.RagDocumentProperties;
import com.example.mallordermilvusrag.dto.DocumentMetadata;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 按文件名从 catalog 解析 metadata；catalog 在 rag.yml 中维护，一次配置、多次复用。
 */
@Component
public class DocumentMetadataRegistry {

    private final RagDocumentProperties properties;

    public DocumentMetadataRegistry(RagDocumentProperties properties) {
        this.properties = properties;
    }

    /**
     * 根据文件名解析 metadata。source 始终为文件名；未命中 catalog 时使用默认值。
     */
    public DocumentMetadata resolve(String filename) {
        String source = filename != null ? filename : "unknown";
        RagDocumentProperties.CatalogEntry entry = properties.catalogByFilename().get(source);

        if (entry != null) {
            return new DocumentMetadata(
                    source,
                    entry.getDepartment(),
                    entry.getRole(),
                    entry.getVersion(),
                    LocalDate.now().toString()
            );
        }

        return new DocumentMetadata(
                source,
                "default",
                "public",
                "1.0",
                LocalDate.now().toString()
        );
    }

    /**
     * 允许上传时显式覆盖 catalog 中的字段（仅非空字段生效）。
     */
    public DocumentMetadata resolve(String filename, String departmentOverride,
                                    String roleOverride, String versionOverride) {
        DocumentMetadata base = resolve(filename);
        if (departmentOverride != null && !departmentOverride.isBlank()) {
            base.setDepartment(departmentOverride);
        }
        if (roleOverride != null && !roleOverride.isBlank()) {
            base.setRole(roleOverride);
        }
        if (versionOverride != null && !versionOverride.isBlank()) {
            base.setVersion(versionOverride);
        }
        return base;
    }
}

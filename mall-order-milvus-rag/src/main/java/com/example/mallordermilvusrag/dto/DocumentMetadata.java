package com.example.mallordermilvusrag.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档元数据（强类型）
 * <p>
 * 替代之前无 schema 的 {@code Map<String, Object>}，
 * 确保 API 输入/输出的 metadata 结构稳定可预期。
 * </p>
 */
public class DocumentMetadata {

    /** 文档来源，如 "售后文档"、"知识库" */
    private String source;

    /** 归属部门，如 "客服"、"运营" */
    private String department;

    /** 角色，如 "客服"、"管理员" */
    private String role;

    /** 版本号，如 "1.0" */
    private String version;

    /** 创建时间，格式自定，如 "2026-06-18" */
    private String createTime;

    public DocumentMetadata() {
    }

    public DocumentMetadata(String source, String department, String role,
                            String version, String createTime) {
        this.source = source;
        this.department = department;
        this.role = role;
        this.version = version;
        this.createTime = createTime;
    }

    // ── getters / setters ──

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    // ── Map 互转 ──

    /**
     * 转为 Spring AI Document 可用的 {@code Map<String, Object>}。
     * null 字段不会写入 Map，避免 Milvus JSON 中堆积无意义的 null 键。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (source != null) map.put("source", source);
        if (department != null) map.put("department", department);
        if (role != null) map.put("role", role);
        if (version != null) map.put("version", version);
        if (createTime != null) map.put("createTime", createTime);
        return map;
    }

    /**
     * 从 Spring AI Document metadata 还原。
     * 宽松解析 — 缺失字段置 null，不抛异常，兼容历史数据和 Milvus 返回的 JSON。
     */
    public static DocumentMetadata fromMap(Map<String, Object> map) {
        if (map == null) {
            return new DocumentMetadata();
        }
        DocumentMetadata m = new DocumentMetadata();
        m.source = stringValue(map.get("source"));
        m.department = stringValue(map.get("department"));
        m.role = stringValue(map.get("role"));
        m.version = stringValue(map.get("version"));
        m.createTime = stringValue(map.get("createTime"));
        return m;
    }

    private static String stringValue(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}

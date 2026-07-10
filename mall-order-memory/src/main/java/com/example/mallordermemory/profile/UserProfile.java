package com.example.mallordermemory.profile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户画像（MySQL user_profile 表）。
 */
public class UserProfile {

    private Long id;
    private String userId;
    private String occupation;
    private String department;
    private String city;
    private String language = "zh-CN";
    private String responseStyle;
    private String profileJson;
    private BigDecimal confidence = new BigDecimal("0.500");
    private int version = 1;
    private String source = "conversation";
    private String lastConversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getResponseStyle() { return responseStyle; }
    public void setResponseStyle(String responseStyle) { this.responseStyle = responseStyle; }
    public String getProfileJson() { return profileJson; }
    public void setProfileJson(String profileJson) { this.profileJson = profileJson; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getLastConversationId() { return lastConversationId; }
    public void setLastConversationId(String lastConversationId) { this.lastConversationId = lastConversationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

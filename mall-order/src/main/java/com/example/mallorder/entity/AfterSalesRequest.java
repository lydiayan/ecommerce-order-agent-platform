package com.example.mallorder.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AfterSalesRequest {
    private String ticketId;
    private String orderId;
    private String userId;
    private String operationType;
    private String reasonType;
    private String reasonDescription;
    private List<String> evidenceUrls;
    private Boolean customerOpened;
    private Boolean customerUsed;
    private String customerConditionStatus;
    private String inspectionResult;
    private String inspectionNote;
    private String eligibilityDecision;
    private String policyVersion;
    private String activeRequestKey;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

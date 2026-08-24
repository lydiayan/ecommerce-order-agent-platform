package com.css.mallorderagent.dto;

public record BadCaseUpdateRequest(String status, String category, String ownerUsername,
                                   String rootCause, String resolution, String fixVersion) {
}

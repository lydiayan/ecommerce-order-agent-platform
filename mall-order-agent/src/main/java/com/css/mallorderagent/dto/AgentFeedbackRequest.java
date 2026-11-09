package com.css.mallorderagent.dto;

import java.util.List;

public record AgentFeedbackRequest(String responseId, String rating, List<String> reasons, String comment) {
}

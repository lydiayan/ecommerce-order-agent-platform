package com.css.mallorderagent.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 单轮对话记录。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationTurn {

    private String userMessage;
    private String assistantMessage;

    public ConversationTurn() {
    }

    public ConversationTurn(String userMessage, String assistantMessage) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public String userMessage() {
        return userMessage;
    }

    public String assistantMessage() {
        return assistantMessage;
    }
}

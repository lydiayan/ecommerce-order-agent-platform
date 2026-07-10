package com.css.mallorderagent.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 组装后的 Prompt。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BuiltPrompt {

    private String systemPrompt;
    private String userMessage;

    public BuiltPrompt() {
    }

    public BuiltPrompt(String systemPrompt, String userMessage) {
        this.systemPrompt = systemPrompt;
        this.userMessage = userMessage;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String userMessage() {
        return userMessage;
    }
}

package com.css.mallorderagent.stream;

public class AgentStreamDisconnectedException extends RuntimeException {

    public AgentStreamDisconnectedException(String message) {
        super(message);
    }

    public AgentStreamDisconnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}

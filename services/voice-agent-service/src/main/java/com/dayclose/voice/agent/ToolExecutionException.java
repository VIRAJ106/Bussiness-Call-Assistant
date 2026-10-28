package com.dayclose.voice.agent;

/**
 * Exception thrown when a tool execution fails.
 */
public class ToolExecutionException extends Exception {
    
    private final String toolName;
    private final String errorCode;
    
    public ToolExecutionException(String toolName, String errorCode, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }
    
    public ToolExecutionException(String toolName, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = errorCode;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

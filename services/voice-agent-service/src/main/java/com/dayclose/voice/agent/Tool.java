package com.dayclose.voice.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A tool the AI agent can invoke (ADR-0009).
 * Each tool is a capability: check stock, create reservation, get price, etc.
 */
public interface Tool {
    
    /**
     * Unique tool name (e.g., "check_stock", "create_reservation").
     */
    String name();
    
    /**
     * Human-readable description for the LLM.
     */
    String description();
    
    /**
     * JSON Schema for tool parameters (OpenAI function calling format).
     */
    JsonNode parametersSchema();
    
    /**
     * Execute the tool with given arguments.
     * 
     * @param tenantId The tenant executing this tool
     * @param arguments Tool arguments as JSON
     * @return Tool result as JSON
     * @throws ToolExecutionException if execution fails
     */
    JsonNode execute(java.util.UUID tenantId, JsonNode arguments) throws ToolExecutionException;
    
    /**
     * Whether this tool requires owner approval (Tier 2).
     */
    default boolean requiresApproval() {
        return false;
    }
}

package com.dayclose.voice.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for LLM providers (ADR-0004).
 * Allows swapping LLM vendors without changing agent logic.
 */
public interface LLMPort {
    
    /**
     * Send a chat completion request to the LLM.
     * 
     * @param tenantId Tenant for cost tracking
     * @param conversationHistory Messages so far
     * @param availableTools Tools the LLM can call
     * @param language Language for response (e.g., "en-IN", "hi-IN")
     * @return LLM response with message and/or tool calls
     */
    LLMResponse chat(
            UUID tenantId,
            List<ConversationMessage> conversationHistory,
            List<ToolDefinition> availableTools,
            String language
    );
}

/**
 * A message in the conversation.
 */
record ConversationMessage(
        String role,    // "user" | "assistant" | "system"
        String content
) {}

/**
 * Tool definition for LLM function calling.
 */
record ToolDefinition(
        String name,
        String description,
        JsonNode parametersSchema
) {}

/**
 * LLM response.
 */
record LLMResponse(
        String message,
        List<LLMToolCall> toolCalls
) {}

/**
 * A tool call requested by the LLM.
 */
record LLMToolCall(
        String name,
        JsonNode arguments
) {}

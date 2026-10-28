package com.dayclose.voice.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OpenAI LLM adapter (implements LLMPort for OpenAI API).
 * Uses GPT-4 with function calling for tool execution.
 */
@Component
public class OpenAILLMAdapter implements LLMPort {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAILLMAdapter.class);
    
    private final RestClient openAIClient;
    private final ObjectMapper objectMapper;
    private final String model;
    
    public OpenAILLMAdapter(
            @Value("${dayclose.llm.openai.api-key}") String apiKey,
            @Value("${dayclose.llm.openai.model:gpt-4-turbo-preview}") String model,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        
        this.openAIClient = restClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        
        this.objectMapper = objectMapper;
        this.model = model;
    }
    
    @Override
    public LLMResponse chat(UUID tenantId,
                           List<ConversationMessage> conversationHistory,
                           List<ToolDefinition> availableTools,
                           String language) {
        
        try {
            // Build request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", model);
            request.put("temperature", 0.7);
            
            // Add system message
            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(createMessage("system", getSystemPrompt(language)));
            
            // Add conversation history
            for (ConversationMessage msg : conversationHistory) {
                messages.add(createMessage(msg.role(), msg.content()));
            }
            
            request.set("messages", messages);
            
            // Add tools (function calling)
            if (!availableTools.isEmpty()) {
                ArrayNode tools = objectMapper.createArrayNode();
                for (ToolDefinition tool : availableTools) {
                    ObjectNode toolDef = objectMapper.createObjectNode();
                    toolDef.put("type", "function");
                    
                    ObjectNode function = objectMapper.createObjectNode();
                    function.put("name", tool.name());
                    function.put("description", tool.description());
                    function.set("parameters", tool.parametersSchema());
                    
                    toolDef.set("function", function);
                    tools.add(toolDef);
                }
                request.set("tools", tools);
                request.put("tool_choice", "auto");
            }
            
            // Call OpenAI
            String responseStr = openAIClient.post()
                    .uri("/chat/completions")
                    .body(request.toString())
                    .retrieve()
                    .body(String.class);
            
            JsonNode response = objectMapper.readTree(responseStr);
            JsonNode choice = response.get("choices").get(0);
            JsonNode message = choice.get("message");
            
            // Extract message content
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText()
                    : "";
            
            // Extract tool calls
            List<LLMToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && !message.get("tool_calls").isNull()) {
                for (JsonNode toolCallNode : message.get("tool_calls")) {
                    JsonNode function = toolCallNode.get("function");
                    String name = function.get("name").asText();
                    JsonNode arguments = objectMapper.readTree(function.get("arguments").asText());
                    
                    toolCalls.add(new LLMToolCall(name, arguments));
                }
            }
            
            return new LLMResponse(content, toolCalls);
            
        } catch (Exception e) {
            log.error("OpenAI API call failed for tenant {}: {}", tenantId, e.getMessage());
            throw new RuntimeException("LLM call failed", e);
        }
    }
    
    private ObjectNode createMessage(String role, String content) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }
    
    private String getSystemPrompt(String language) {
        return """
            You are an AI assistant for DayClose, helping small business owners manage their inventory.
            
            You are talking to a customer on the phone. Your role is to:
            - Answer questions about product availability
            - Check stock levels
            - Create reservations for customer orders
            - Be helpful, professional, and accurate
            
            IMPORTANT RULES:
            1. Always use tools to check stock - never guess or make up numbers
            2. When mentioning quantities, prices, or availability, they MUST come from tool results
            3. You can create reservations (Tier 1), but tell customers the owner will confirm within 30 minutes
            4. Never promise delivery dates, discounts, or special terms without owner approval
            5. If you don't know something, say so - don't make it up
            6. Be concise and natural - this is a phone conversation
            
            Language: %s (respond in this language)
            """.formatted(language);
    }
}

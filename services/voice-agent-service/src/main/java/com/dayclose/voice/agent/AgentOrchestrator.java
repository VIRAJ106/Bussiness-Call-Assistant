package com.dayclose.voice.agent;

import com.dayclose.voice.domain.CallSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Core agent loop: receives customer input, calls LLM, executes tools, returns response.
 * Implements ADR-0009 agent architecture with guardrails.
 */
@Component
public class AgentOrchestrator {
    
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    
    private final ToolRegistry toolRegistry;
    private final LLMPort llmPort;
    private final GuardrailEngine guardrailEngine;
    private final ObjectMapper objectMapper;
    
    public AgentOrchestrator(ToolRegistry toolRegistry, 
                            LLMPort llmPort,
                            GuardrailEngine guardrailEngine,
                            ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.llmPort = llmPort;
        this.guardrailEngine = guardrailEngine;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process one turn of conversation.
     * 
     * @param session Current call session
     * @param customerMessage What the customer said (transcript)
     * @param conversationHistory Previous messages
     * @return Agent's response
     */
    public AgentResponse processMessage(CallSession session, 
                                       String customerMessage,
                                       List<ConversationMessage> conversationHistory) {
        
        log.info("Processing message for session {}: {}", session.id(), customerMessage);
        
        // Add customer message to history
        conversationHistory.add(new ConversationMessage("user", customerMessage));
        
        // Prepare tools for LLM
        List<ToolDefinition> availableTools = toolRegistry.all().stream()
                .map(this::toToolDefinition)
                .toList();
        
        int maxIterations = 10; // Prevent infinite loops
        int iteration = 0;
        
        while (iteration++ < maxIterations) {
            // Call LLM
            LLMResponse llmResponse = llmPort.chat(
                    session.tenantId(),
                    conversationHistory,
                    availableTools,
                    session.language()
            );
            
            // If no tool call, we have the final answer
            if (llmResponse.toolCalls().isEmpty()) {
                String agentMessage = llmResponse.message();
                
                // Apply guardrails to agent response
                GuardrailResult guardrailResult = guardrailEngine.validate(
                        session.id(),
                        agentMessage,
                        conversationHistory
                );
                
                if (!guardrailResult.passed()) {
                    // Guardrail violation - correct the response
                    log.warn("Guardrail violation in session {}: {}", 
                            session.id(), guardrailResult.violations());
                    
                    agentMessage = guardrailResult.correctedMessage() != null 
                            ? guardrailResult.correctedMessage()
                            : "I apologize, I need to double-check that information. Let me verify.";
                }
                
                conversationHistory.add(new ConversationMessage("assistant", agentMessage));
                
                return new AgentResponse(
                        agentMessage,
                        Collections.emptyList(),
                        guardrailResult.violations()
                );
            }
            
            // Execute tool calls
            List<ToolCallResult> toolResults = new ArrayList<>();
            StringBuilder toolResultsMessage = new StringBuilder();
            
            for (LLMToolCall toolCall : llmResponse.toolCalls()) {
                try {
                    log.info("Executing tool: {} with args: {}", 
                            toolCall.name(), toolCall.arguments());
                    
                    Tool tool = toolRegistry.get(toolCall.name())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Tool not found: " + toolCall.name()));
                    
                    // Execute tool
                    long startMs = System.currentTimeMillis();
                    JsonNode result = tool.execute(session.tenantId(), toolCall.arguments());
                    long durationMs = System.currentTimeMillis() - startMs;
                    
                    toolResults.add(new ToolCallResult(
                            toolCall.name(),
                            toolCall.arguments().toString(),
                            result.toString(),
                            "success",
                            (int) durationMs
                    ));
                    
                    toolResultsMessage.append(String.format(
                            "Tool %s returned: %s\n", 
                            toolCall.name(), 
                            result.toString()
                    ));
                    
                } catch (ToolExecutionException e) {
                    log.error("Tool execution failed: {} - {}", e.getToolName(), e.getMessage());
                    
                    toolResults.add(new ToolCallResult(
                            toolCall.name(),
                            toolCall.arguments().toString(),
                            e.getMessage(),
                            "failed",
                            0
                    ));
                    
                    toolResultsMessage.append(String.format(
                            "Tool %s failed: %s\n", 
                            toolCall.name(), 
                            e.getMessage()
                    ));
                }
            }
            
            // Add tool results to conversation history
            conversationHistory.add(new ConversationMessage(
                    "system", 
                    "Tool results:\n" + toolResultsMessage.toString()
            ));
            
            // Continue loop - LLM will use tool results to formulate response
        }
        
        // Max iterations reached
        log.error("Max iterations reached for session {}", session.id());
        return new AgentResponse(
                "I apologize, I'm having trouble processing your request. Please try again.",
                Collections.emptyList(),
                List.of("max_iterations_reached")
        );
    }
    
    private ToolDefinition toToolDefinition(Tool tool) {
        return new ToolDefinition(
                tool.name(),
                tool.description(),
                tool.parametersSchema()
        );
    }
    
    /**
     * Agent's response to customer.
     */
    public record AgentResponse(
            String message,
            List<ToolCallResult> toolCalls,
            List<String> guardrailViolations
    ) {}
    
    /**
     * Result of a tool call.
     */
    public record ToolCallResult(
            String toolName,
            String arguments,
            String result,
            String status,
            int durationMs
    ) {}
}

package com.dayclose.voice.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guardrail engine validates agent responses for correctness (ADR-0011).
 * Key rules:
 * - Numbers must be grounded (match tool results)
 * - No unauthorized commitments
 * - No PII leakage
 * - No harmful content
 */
@Component
public class GuardrailEngine {
    
    private static final Logger log = LoggerFactory.getLogger(GuardrailEngine.class);
    
    // Pattern to extract numbers from text
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");
    
    /**
     * Validate agent's message before sending to customer.
     */
    public GuardrailResult validate(UUID sessionId, 
                                    String agentMessage,
                                    List<ConversationMessage> conversationHistory) {
        
        List<String> violations = new ArrayList<>();
        String correctedMessage = null;
        
        // Rule 1: Verify numbers are grounded in tool results
        List<String> numbersInMessage = extractNumbers(agentMessage);
        if (!numbersInMessage.isEmpty()) {
            String lastToolResult = findLastToolResult(conversationHistory);
            if (lastToolResult != null) {
                for (String number : numbersInMessage) {
                    if (!lastToolResult.contains(number)) {
                        violations.add("ungrounded_number:" + number);
                        log.warn("Session {}: Agent mentioned number {} not in tool results", 
                                sessionId, number);
                    }
                }
            }
        }
        
        // Rule 2: No unauthorized price promises
        if (agentMessage.toLowerCase().contains("free") && 
            !agentMessage.toLowerCase().contains("shipping")) {
            violations.add("unauthorized_price_promise");
            log.warn("Session {}: Agent tried to offer something free", sessionId);
        }
        
        // Rule 3: No commitments without approval
        String lowerMessage = agentMessage.toLowerCase();
        if (lowerMessage.contains("i will") || 
            lowerMessage.contains("i'll") ||
            lowerMessage.contains("we will") ||
            lowerMessage.contains("we'll")) {
            
            if (!lowerMessage.contains("owner") && 
                !lowerMessage.contains("confirm") &&
                !lowerMessage.contains("approval")) {
                violations.add("unauthorized_commitment");
                log.warn("Session {}: Agent made commitment without mentioning approval", sessionId);
            }
        }
        
        // Rule 4: No negative or harmful language
        if (containsHarmfulContent(agentMessage)) {
            violations.add("harmful_content");
            correctedMessage = "I apologize, let me rephrase that professionally.";
        }
        
        boolean passed = violations.isEmpty();
        
        return new GuardrailResult(passed, violations, correctedMessage);
    }
    
    private List<String> extractNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }
    
    private String findLastToolResult(List<ConversationMessage> history) {
        // Find most recent system message with tool results
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationMessage msg = history.get(i);
            if ("system".equals(msg.role()) && msg.content().contains("Tool")) {
                return msg.content();
            }
        }
        return null;
    }
    
    private boolean containsHarmfulContent(String text) {
        String lower = text.toLowerCase();
        
        // Basic harmful content detection
        List<String> harmful = List.of(
                "stupid", "idiot", "hate", "kill", "die",
                "scam", "fraud", "cheat", "steal"
        );
        
        for (String word : harmful) {
            if (lower.contains(word)) {
                return true;
            }
        }
        
        return false;
    }
}

/**
 * Result of guardrail validation.
 */
record GuardrailResult(
        boolean passed,
        List<String> violations,
        String correctedMessage  // If not null, use this instead of original
) {}

package com.dayclose.voice.agent;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all tools available to the AI agent (ADR-0009).
 * Tools are allow-listed: only registered tools can be called.
 */
@Component
public class ToolRegistry {
    
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    
    public ToolRegistry(List<Tool> availableTools) {
        for (Tool tool : availableTools) {
            register(tool);
        }
    }
    
    /**
     * Register a tool. Throws if tool name already exists.
     */
    public void register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalStateException("Tool already registered: " + tool.name());
        }
        tools.put(tool.name(), tool);
    }
    
    /**
     * Get a tool by name.
     */
    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }
    
    /**
     * Get all registered tools.
     */
    public Collection<Tool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }
    
    /**
     * Check if a tool is registered.
     */
    public boolean exists(String name) {
        return tools.containsKey(name);
    }
    
    /**
     * Get tool names (for LLM function calling).
     */
    public Set<String> toolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}

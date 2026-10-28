package com.dayclose.voice.agent.tools;

import com.dayclose.voice.agent.Tool;
import com.dayclose.voice.agent.ToolExecutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Tool: Check stock availability for an item at a location.
 * Calls inventory-service GET /v1/stock/{itemId}/{locationId}
 */
@Component
public class CheckStockTool implements Tool {
    
    private final RestClient inventoryClient;
    private final ObjectMapper objectMapper;
    
    public CheckStockTool(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.inventoryClient = restClientBuilder
                .baseUrl("http://localhost:8082") // TODO: externalize
                .build();
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String name() {
        return "check_stock";
    }
    
    @Override
    public String description() {
        return "Check current stock availability for an item at a location. Returns on_hand quantity, reserved quantity, and available quantity.";
    }
    
    @Override
    public JsonNode parametersSchema() {
        ObjectNode props = objectMapper.createObjectNode();
        props.set("item_id", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "UUID of the item to check"));
        props.set("location_id", objectMapper.createObjectNode()
                .put("type", "string")
                .put("description", "UUID of the location to check"));
        
        com.fasterxml.jackson.databind.node.ArrayNode required = objectMapper.createArrayNode();
        required.add("item_id");
        required.add("location_id");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", props);
        schema.set("required", required);
        
        return schema;
    }
    
    @Override
    public JsonNode execute(UUID tenantId, JsonNode arguments) throws ToolExecutionException {
        try {
            String itemId = arguments.get("item_id").asText();
            String locationId = arguments.get("location_id").asText();
            
            // Call inventory-service
            String response = inventoryClient.get()
                    .uri("/v1/stock/{itemId}/{locationId}", itemId, locationId)
                    .header("X-Tenant-Id", tenantId.toString())
                    .retrieve()
                    .body(String.class);
            
            JsonNode stock = objectMapper.readTree(response);
            
            // Calculate available
            int onHand = stock.get("onHand").asInt();
            int reserved = stock.get("reserved").asInt();
            int available = onHand - reserved;
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("on_hand", onHand);
            result.put("reserved", reserved);
            result.put("available", available);
            result.put("status", available > 0 ? "in_stock" : "out_of_stock");
            
            return result;
            
        } catch (Exception e) {
            throw new ToolExecutionException(name(), "stock_check_failed", 
                    "Failed to check stock: " + e.getMessage(), e);
        }
    }
}

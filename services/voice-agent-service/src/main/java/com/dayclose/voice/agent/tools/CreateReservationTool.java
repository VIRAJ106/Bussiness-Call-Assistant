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
 * Tool: Create a stock reservation (Tier 1 - agent can do this during call).
 * Calls inventory-service POST /v1/reservations
 */
@Component
public class CreateReservationTool implements Tool {
    
    private final RestClient inventoryClient;
    private final ObjectMapper objectMapper;
    
    public CreateReservationTool(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.inventoryClient = restClientBuilder
                .baseUrl("http://localhost:8082")
                .build();
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String name() {
        return "create_reservation";
    }
    
    @Override
    public String description() {
        return "Create a temporary stock reservation for a customer order. The reservation will expire in 30 minutes unless the owner approves it. Use this when a customer wants to order something.";
    }
    
    @Override
    public JsonNode parametersSchema() {
        ObjectNode commitmentProp = objectMapper.createObjectNode();
        commitmentProp.put("type", "string");
        commitmentProp.put("description", "UUID linking this reservation to a commitment");
        
        ObjectNode itemProp = objectMapper.createObjectNode();
        itemProp.put("type", "string");
        itemProp.put("description", "UUID of the item");
        
        ObjectNode locationProp = objectMapper.createObjectNode();
        locationProp.put("type", "string");
        locationProp.put("description", "UUID of the location");
        
        ObjectNode qtyProp = objectMapper.createObjectNode();
        qtyProp.put("type", "integer");
        qtyProp.put("description", "Quantity to reserve (must be positive)");
        
        ObjectNode customerProp = objectMapper.createObjectNode();
        customerProp.put("type", "string");
        customerProp.put("description", "Customer reference (phone, name, etc.)");
        
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("commitment_id", commitmentProp);
        properties.set("item_id", itemProp);
        properties.set("location_id", locationProp);
        properties.set("qty", qtyProp);
        properties.set("customer_ref", customerProp);
        
        com.fasterxml.jackson.databind.node.ArrayNode required = objectMapper.createArrayNode();
        required.add("commitment_id");
        required.add("item_id");
        required.add("location_id");
        required.add("qty");
        required.add("customer_ref");
        
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.set("required", required);
        
        return schema;
    }
    
    @Override
    public JsonNode execute(UUID tenantId, JsonNode arguments) throws ToolExecutionException {
        try {
            // Build request body
            ObjectNode request = objectMapper.createObjectNode();
            request.put("commitmentId", arguments.get("commitment_id").asText());
            request.put("itemId", arguments.get("item_id").asText());
            request.put("locationId", arguments.get("location_id").asText());
            request.put("qty", arguments.get("qty").asInt());
            request.put("customerRef", arguments.get("customer_ref").asText());
            request.put("ttl", "30 minutes");
            
            // Call inventory-service
            String response = inventoryClient.post()
                    .uri("/v1/reservations")
                    .header("X-Tenant-Id", tenantId.toString())
                    .header("Content-Type", "application/json")
                    .body(request.toString())
                    .retrieve()
                    .body(String.class);
            
            JsonNode result = objectMapper.readTree(response);
            
            ObjectNode agentResponse = objectMapper.createObjectNode();
            agentResponse.put("reservation_id", result.get("reservation_id").asText());
            agentResponse.put("status", "reserved");
            agentResponse.put("qty", arguments.get("qty").asInt());
            agentResponse.put("expires_in_minutes", 30);
            agentResponse.put("message", "Stock reserved successfully. Owner will confirm within 30 minutes.");
            
            return agentResponse;
            
        } catch (Exception e) {
            throw new ToolExecutionException(name(), "reservation_failed", 
                    "Failed to create reservation: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean requiresApproval() {
        return false; // Tier 1 - agent can reserve during call
    }
}

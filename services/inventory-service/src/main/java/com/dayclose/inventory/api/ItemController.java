package com.dayclose.inventory.api;

import com.dayclose.inventory.api.dto.CreateItemRequest;
import com.dayclose.inventory.api.dto.UpdateItemRequest;
import com.dayclose.inventory.domain.Item;
import com.dayclose.inventory.infra.ItemRepository;
import com.dayclose.web.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for items (SKUs, products) in the inventory (ADR-0005).
 */
@RestController
@RequestMapping("/v1/items")
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @PostMapping
    public ResponseEntity<Item> createItem(@Valid @RequestBody CreateItemRequest request) {
        UUID tenantId = TenantContext.require();

        // Check for duplicate SKU
        if (itemRepository.findBySku(tenantId, request.sku()).isPresent()) {
            throw new IllegalArgumentException("Item with SKU '" + request.sku() + "' already exists");
        }

        Item item = itemRepository.create(
                tenantId,
                request.sku(),
                request.name(),
                request.unit(),
                request.pricePaise(),
                request.currency(),
                request.reorderPoint() != null ? request.reorderPoint() : 0,
                request.reorderQty() != null ? request.reorderQty() : 0
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> getItem(@PathVariable UUID id) {
        UUID tenantId = TenantContext.require();

        return itemRepository.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Item>> listItems(@RequestParam(defaultValue = "100") int limit) {
        UUID tenantId = TenantContext.require();

        if (limit > 100 || limit < 1) {
            limit = 100;
        }

        List<Item> items = itemRepository.list(tenantId, limit);
        return ResponseEntity.ok(items);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Item> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateItemRequest request) {
        UUID tenantId = TenantContext.require();

        Item existing = itemRepository.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        Item updated = new Item(
                existing.id(),
                existing.tenantId(),
                existing.sku(), // SKU cannot be changed
                request.name(),
                request.unit() != null ? request.unit() : existing.unit(),
                request.pricePaise(),
                request.currency() != null ? request.currency() : existing.currency(),
                request.reorderPoint() != null ? request.reorderPoint() : existing.reorderPoint(),
                request.reorderQty() != null ? request.reorderQty() : existing.reorderQty(),
                request.active() != null ? request.active() : existing.active()
        );

        itemRepository.update(updated);

        return ResponseEntity.ok(updated);
    }
}

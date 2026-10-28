package com.dayclose.inventory.api;

import com.dayclose.inventory.api.dto.AdjustStockRequest;
import com.dayclose.inventory.api.dto.ReceiveStockRequest;
import com.dayclose.inventory.domain.StockLevel;
import com.dayclose.inventory.infra.StockRepository;
import com.dayclose.web.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for stock operations: receiving, adjusting, and querying stock levels (ADR-0005).
 */
@RestController
@RequestMapping("/v1/stock")
public class StockController {

    private final StockRepository stockRepository;

    public StockController(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @PostMapping("/receive")
    public ResponseEntity<Void> receiveStock(@Valid @RequestBody ReceiveStockRequest request) {
        UUID tenantId = TenantContext.require();

        stockRepository.receiveStock(
                tenantId,
                request.itemId(),
                request.locationId(),
                request.qty(),
                request.actor()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/adjust")
    public ResponseEntity<Void> adjustStock(@Valid @RequestBody AdjustStockRequest request) {
        UUID tenantId = TenantContext.require();

        stockRepository.adjustStock(
                tenantId,
                request.itemId(),
                request.locationId(),
                request.delta(),
                request.actor(),
                request.reasonNote()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<StockLevel>> listStock(
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId) {
        UUID tenantId = TenantContext.require();

        List<StockLevel> levels = stockRepository.listStock(tenantId, itemId, locationId);
        return ResponseEntity.ok(levels);
    }

    @GetMapping("/{itemId}/{locationId}")
    public ResponseEntity<StockLevel> getStockLevel(
            @PathVariable UUID itemId,
            @PathVariable UUID locationId) {
        UUID tenantId = TenantContext.require();

        return stockRepository.getStockLevel(tenantId, itemId, locationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

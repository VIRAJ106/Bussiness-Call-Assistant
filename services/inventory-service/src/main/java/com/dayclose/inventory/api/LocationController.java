package com.dayclose.inventory.api;

import com.dayclose.inventory.api.dto.CreateLocationRequest;
import com.dayclose.inventory.domain.Location;
import com.dayclose.inventory.infra.LocationRepository;
import com.dayclose.web.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for warehouse/store locations (ADR-0005).
 */
@RestController
@RequestMapping("/v1/locations")
public class LocationController {

    private final LocationRepository locationRepository;

    public LocationController(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @PostMapping
    public ResponseEntity<Location> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        UUID tenantId = TenantContext.require();

        Location location = locationRepository.create(tenantId, request.name());

        return ResponseEntity.status(HttpStatus.CREATED).body(location);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Location> getLocation(@PathVariable UUID id) {
        UUID tenantId = TenantContext.require();

        return locationRepository.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Location>> listLocations() {
        UUID tenantId = TenantContext.require();

        List<Location> locations = locationRepository.list(tenantId);
        return ResponseEntity.ok(locations);
    }
}

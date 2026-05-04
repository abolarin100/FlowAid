package com.flowaid.controller;

import com.flowaid.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated analytics and operational metrics")
@CrossOrigin(origins = "${flowaid.cors.allowed-origins}")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Get platform-wide summary statistics")
    public ResponseEntity<DashboardService.DashboardStats> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }
}

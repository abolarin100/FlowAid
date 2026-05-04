package com.flowaid.controller;

import com.flowaid.dto.CampaignDto;
import com.flowaid.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns", description = "Cash transfer campaign management")
@CrossOrigin(origins = "${flowaid.cors.allowed-origins}")
public class CampaignController {

    private final CampaignService campaignService;

    // GET /api/v1/campaigns
    @GetMapping
    @Operation(summary = "List all campaigns")
    public ResponseEntity<List<CampaignDto.Response>> list() {
        return ResponseEntity.ok(campaignService.listAll());
    }

    // GET /api/v1/campaigns/{id}
    @GetMapping("/{id}")
    @Operation(summary = "Get a single campaign by ID")
    public ResponseEntity<CampaignDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.getById(id));
    }

    // POST /api/v1/campaigns
    @PostMapping
    @Operation(summary = "Create a new campaign")
    public ResponseEntity<CampaignDto.Response> create(
        @Valid @RequestBody CampaignDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(campaignService.create(request));
    }

    // PATCH /api/v1/campaigns/{id}/status
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update campaign status (e.g. DRAFT → ACTIVE)")
    public ResponseEntity<CampaignDto.Response> updateStatus(
        @PathVariable UUID id,
        @Valid @RequestBody CampaignDto.StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(campaignService.updateStatus(id, request.getStatus()));
    }
}

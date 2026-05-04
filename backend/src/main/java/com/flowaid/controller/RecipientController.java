package com.flowaid.controller;

import com.flowaid.dto.RecipientDto;
import com.flowaid.service.RecipientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recipients")
@RequiredArgsConstructor
@Tag(name = "Recipients", description = "Recipient enrollment and management")
@CrossOrigin(origins = "${flowaid.cors.allowed-origins}")
public class RecipientController {

    private final RecipientService recipientService;

    // GET /api/v1/recipients?page=0&size=25
    @GetMapping
    @Operation(summary = "List all recipients with pagination")
    public ResponseEntity<Page<RecipientDto.Response>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(recipientService.listRecipients(pageable));
    }

    // GET /api/v1/recipients/{id}
    @GetMapping("/{id}")
    @Operation(summary = "Get a single recipient by ID")
    public ResponseEntity<RecipientDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(recipientService.getById(id));
    }

    // POST /api/v1/recipients
    @PostMapping
    @Operation(summary = "Enroll a new recipient")
    public ResponseEntity<RecipientDto.Response> create(
        @Valid @RequestBody RecipientDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(recipientService.create(request));
    }

    // PATCH /api/v1/recipients/{id}/status
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update a recipient's enrollment status")
    public ResponseEntity<RecipientDto.Response> updateStatus(
        @PathVariable UUID id,
        @Valid @RequestBody RecipientDto.StatusUpdateRequest request
    ) {
        return ResponseEntity.ok(recipientService.updateStatus(id, request.getStatus()));
    }
}

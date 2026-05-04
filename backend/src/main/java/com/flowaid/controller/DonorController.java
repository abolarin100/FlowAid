package com.flowaid.controller;

import com.flowaid.dto.DonorDto;
import com.flowaid.service.DonorService;
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
@RequestMapping("/api/v1/donors")
@RequiredArgsConstructor
@Tag(name = "Donors", description = "Donor registration and management")
@CrossOrigin(origins = "${flowaid.cors.allowed-origins}")
public class DonorController {

    private final DonorService donorService;

    // GET /api/v1/donors?page=0&size=25
    @GetMapping
    @Operation(summary = "List all donors with pagination")
    public ResponseEntity<Page<DonorDto.Response>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(donorService.listDonors(pageable));
    }

    // GET /api/v1/donors/{id}
    @GetMapping("/{id}")
    @Operation(summary = "Get a single donor by ID")
    public ResponseEntity<DonorDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(donorService.getById(id));
    }

    // POST /api/v1/donors
    @PostMapping
    @Operation(summary = "Register a new donor")
    public ResponseEntity<DonorDto.Response> create(
        @Valid @RequestBody DonorDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(donorService.create(request));
    }
}

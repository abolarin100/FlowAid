package com.flowaid.controller;

import com.flowaid.dto.PaymentDto;
import com.flowaid.service.PaymentService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Cash transfer disbursement operations")
@CrossOrigin(origins = "${flowaid.cors.allowed-origins}")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate a single payment to a recipient")
    public ResponseEntity<PaymentDto.Response> initiatePayment(
        @Valid @RequestBody PaymentDto.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(paymentService.initiatePayment(request));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk disbursement to multiple recipients in a campaign")
    public ResponseEntity<List<PaymentDto.Response>> bulkDisbursement(
        @Valid @RequestBody PaymentDto.BulkDisbursementRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(paymentService.bulkDisbursement(request));
    }

    @GetMapping("/campaign/{campaignId}")
    @Operation(summary = "Get all payments for a campaign")
    public ResponseEntity<Page<PaymentDto.Response>> getPaymentsByCampaign(
        @PathVariable UUID campaignId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByCampaign(campaignId, pageable));
    }

    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Get payment history for a recipient")
    public ResponseEntity<Page<PaymentDto.Response>> getPaymentsByRecipient(
        @PathVariable UUID recipientId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByRecipient(recipientId, pageable));
    }
}

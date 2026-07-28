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
            @Valid @RequestBody PaymentDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiatePayment(request));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk disbursement to multiple recipients in a campaign")
    public ResponseEntity<List<PaymentDto.Response>> bulkDisbursement(
            @Valid @RequestBody PaymentDto.BulkDisbursementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.bulkDisbursement(request));
    }

    @GetMapping("/campaign/{campaignId}")
    @Operation(summary = "Get all payments for a campaign")
    public ResponseEntity<Page<PaymentDto.Response>> getPaymentsByCampaign(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByCampaign(campaignId, pageable));
    }

    @GetMapping("/recipient/{recipientId}")
    @Operation(summary = "Get payment history for a recipient")
    public ResponseEntity<Page<PaymentDto.Response>> getPaymentsByRecipient(
            @PathVariable UUID recipientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getPaymentsByRecipient(recipientId, pageable));
    }

    @GetMapping
    @Operation(summary = "Get all payments (paginated)")
    public ResponseEntity<Page<PaymentDto.Response>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(paymentService.getAllPayments(pageable));
    }

    @GetMapping("/failure-queue")
    @Operation(summary = "Payments that are FAILED, RETRY_SCHEDULED, or DEAD_LETTER — the ops retry queue")
    public ResponseEntity<Page<PaymentDto.Response>> getFailureQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("nextRetryAt").ascending());
        return ResponseEntity.ok(paymentService.getFailureQueue(pageable));
    }

    @GetMapping("/failure-queue/count")
    @Operation(summary = "Count of unresolved payments needing attention")
    public ResponseEntity<Long> getFailureQueueCount() {
        return ResponseEntity.ok(paymentService.getFailureQueueCount());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Manually trigger a retry for a failed/dead-lettered payment")
    public ResponseEntity<Void> retryPayment(@PathVariable UUID id) {
        paymentService.retryPayment(id, "manual-ops");
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/audit-trail")
    @Operation(summary = "Full status-transition history for a payment")
    public ResponseEntity<List<com.flowaid.model.PaymentAuditLog>> getAuditTrail(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getAuditTrail(id));
    }
}

package com.flowaid.controller;

import com.flowaid.dto.DonationDto;
import com.flowaid.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Donations", description = "Donor checkout, Stripe webhooks, donation history and impact reporting")
@CrossOrigin(origins = "${flowaid.cors.allowed-origins}")
public class DonationController {

    private final DonationService donationService;

    // POST /api/v1/donations/checkout-session
    @PostMapping("/api/v1/donations/checkout-session")
    @Operation(summary = "Create a Stripe Checkout session for a one-time or recurring donation")
    public ResponseEntity<DonationDto.CheckoutResponse> createCheckoutSession(
            @Valid @RequestBody DonationDto.CheckoutRequest request) {
        return ResponseEntity.ok(donationService.createCheckoutSession(request));
    }

    // GET /api/v1/donors/{donorId}/donations
    @GetMapping("/api/v1/donors/{donorId}/donations")
    @Operation(summary = "Donation history for a donor")
    public ResponseEntity<Page<DonationDto.Response>> getDonationHistory(
            @PathVariable UUID donorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(donationService.getDonationHistory(donorId, pageable));
    }

    // GET /api/v1/donors/{donorId}/impact
    @GetMapping("/api/v1/donors/{donorId}/impact")
    @Operation(summary = "\"Your $50 funded X recipients\" impact summary for a donor")
    public ResponseEntity<DonationDto.ImpactSummary> getImpact(@PathVariable UUID donorId) {
        return ResponseEntity.ok(donationService.getImpact(donorId));
    }

    // POST /api/v1/webhooks/stripe
    // NOTE: this endpoint must receive the RAW request body (not a parsed
    // object) because Stripe's signature is computed over the exact bytes
    // sent. Make sure SecurityConfig permits this path without CSRF/auth and
    // that no filter has already consumed/parsed the body upstream.
    @PostMapping("/api/v1/webhooks/stripe")
    @Operation(summary = "Stripe webhook receiver (checkout completion, payment failure, etc.)")
    public ResponseEntity<String> handleStripeWebhook(HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader) throws IOException {
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        donationService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok("received");
    }
}

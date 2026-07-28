package com.flowaid.dto;

import com.flowaid.model.Donation.DonationStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DonationDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CheckoutRequest {
        @NotNull(message = "Donor ID is required")
        private UUID donorId;

        // Optional: earmark the donation for a specific campaign.
        private UUID campaignId;

        @NotNull @DecimalMin(value = "1.00", message = "Minimum donation is $1")
        private BigDecimal amountUsd;

        @Builder.Default
        private Boolean isRecurring = false;

        @NotBlank(message = "successUrl is required (where Stripe redirects after payment)")
        private String successUrl;

        @NotBlank(message = "cancelUrl is required (where Stripe redirects if the donor cancels)")
        private String cancelUrl;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CheckoutResponse {
        private UUID donationId;
        private String checkoutUrl;
        private String stripeSessionId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private UUID donorId;
        private UUID campaignId;
        private String campaignName;
        private BigDecimal amountUsd;
        private String currency;
        private DonationStatus status;
        private Boolean isRecurring;
        private Instant createdAt;
    }

    // "Your $50 funded X recipients" impact view for the donor dashboard.
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ImpactSummary {
        private UUID donorId;
        private BigDecimal totalDonatedUsd;
        private long donationCount;
        private long estimatedRecipientsFunded;
        private String note;
    }
}

package com.flowaid.dto;

import com.flowaid.model.Payment.PaymentStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "Recipient ID is required")
        private UUID recipientId;

        @NotNull(message = "Campaign ID is required")
        private UUID campaignId;

        @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive")
        @Digits(integer = 13, fraction = 2)
        private BigDecimal amount;

        @NotBlank @Size(min = 3, max = 3)
        private String currency;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private UUID recipientId;
        private String recipientName;
        private UUID campaignId;
        private String campaignName;
        private BigDecimal amount;
        private String currency;
        private PaymentStatus status;
        private String externalTransferId;
        private String failureReason;
        private Instant initiatedAt;
        private Instant completedAt;
        private Instant createdAt;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdate {
        @NotNull
        private PaymentStatus status;
        private String externalTransferId;
        private String failureReason;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BulkDisbursementRequest {
        @NotNull private UUID campaignId;
        @NotEmpty private java.util.List<UUID> recipientIds;
    }
}

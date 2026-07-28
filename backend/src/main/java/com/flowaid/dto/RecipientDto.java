package com.flowaid.dto;

import com.flowaid.model.Recipient.EnrollmentStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

public class RecipientDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        private String lastName;

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        private String phoneNumber;

        @NotBlank(message = "Country code is required")
        @Size(min = 2, max = 2, message = "Country code must be ISO 2-letter code e.g. NG")
        private String countryCode;

        @Size(max = 100)
        private String region;

        @Size(max = 50)
        private String preferredPaymentMethod;

        // Inputs to the eligibility engine. vulnerabilityScore is no longer
        // accepted from the client — it's computed server-side from these.
        @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "Income cannot be negative")
        private java.math.BigDecimal monthlyIncomeUsd;

        @Min(1) @Max(30)
        private Integer householdSize;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdateRequest {
        @NotNull(message = "Status is required")
        private EnrollmentStatus status;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String countryCode;
        private String region;
        private String preferredPaymentMethod;
        private EnrollmentStatus enrollmentStatus;
        private Integer vulnerabilityScore;
        private java.math.BigDecimal monthlyIncomeUsd;
        private Integer householdSize;
        private com.flowaid.model.Recipient.EligibilityDecision eligibilityDecision;
        private String eligibilityReason;
        private Instant createdAt;
        private Instant updatedAt;
    }
}

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

        @Min(0) @Max(100)
        private Integer vulnerabilityScore;
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
        private Instant createdAt;
        private Instant updatedAt;
    }
}

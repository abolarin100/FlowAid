package com.flowaid.dto;

import com.flowaid.model.Donor.DonorTier;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DonorDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        private String email;

        @Size(max = 200)
        private String organizationName;

        private Boolean isRecurring;

        private String stripeCustomerId;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String organizationName;
        private DonorTier donorTier;
        private BigDecimal totalDonatedUsd;
        private Boolean isRecurring;
        private Instant createdAt;
        private Instant updatedAt;
    }
}

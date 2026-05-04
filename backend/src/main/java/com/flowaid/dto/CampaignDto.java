package com.flowaid.dto;

import com.flowaid.model.Campaign.CampaignStatus;
import com.flowaid.model.Campaign.CampaignType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CampaignDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "Campaign name is required")
        @Size(max = 200)
        private String name;

        private String description;

        @NotNull(message = "Campaign type is required")
        private CampaignType type;

        @Size(min = 2, max = 2, message = "Target country must be ISO 2-letter code e.g. NG")
        private String targetCountry;

        @Size(max = 100)
        private String targetRegion;

        @NotNull(message = "Budget is required")
        @DecimalMin(value = "0.01", message = "Budget must be positive")
        @Digits(integer = 13, fraction = 2)
        private BigDecimal budgetUsd;

        @NotNull(message = "Transfer amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be positive")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal transferAmountUsd;

        private LocalDate startDate;
        private LocalDate endDate;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusUpdateRequest {
        @NotNull(message = "Status is required")
        private CampaignStatus status;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String name;
        private String description;
        private CampaignType type;
        private CampaignStatus status;
        private String targetCountry;
        private String targetRegion;
        private BigDecimal budgetUsd;
        private BigDecimal disbursedUsd;
        private BigDecimal transferAmountUsd;
        private LocalDate startDate;
        private LocalDate endDate;
        private Instant createdAt;
        private Instant updatedAt;
    }
}

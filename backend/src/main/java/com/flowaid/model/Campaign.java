package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "target_country", length = 2)
    private String targetCountry;

    @Column(name = "target_region", length = 100)
    private String targetRegion;

    @Column(name = "budget_usd", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetUsd;

    @Column(name = "disbursed_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal disbursedUsd = BigDecimal.ZERO;

    @Column(name = "transfer_amount_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal transferAmountUsd;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Payment> payments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum CampaignType {
        EMERGENCY_RELIEF, LONG_TERM_TRANSFER, CRISIS_RESPONSE, PILOT
    }

    public enum CampaignStatus {
        DRAFT, ACTIVE, PAUSED, COMPLETED, ARCHIVED
    }
}

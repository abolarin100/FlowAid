package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "donors", indexes = {
    @Index(name = "idx_donor_email", columnList = "email"),
    @Index(name = "idx_donor_tier", columnList = "donor_tier")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "organization_name", length = 200)
    private String organizationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "donor_tier", length = 20)
    private DonorTier donorTier;

    @Column(name = "total_donated_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDonatedUsd = BigDecimal.ZERO;

    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (donorTier == null) donorTier = DonorTier.STANDARD;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum DonorTier {
        STANDARD, SILVER, GOLD, PLATINUM, INSTITUTIONAL
    }
}

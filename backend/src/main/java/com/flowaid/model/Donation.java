package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An incoming payment from a Donor, optionally earmarked for a Campaign.
 * Distinct from Payment (which models outbound disbursement to a Recipient) —
 * Donation is the "money in" side, Payment is the "money out" side.
 */
@Entity
@Table(name = "donations", indexes = {
    @Index(name = "idx_donation_donor", columnList = "donor_id"),
    @Index(name = "idx_donation_campaign", columnList = "campaign_id"),
    @Index(name = "idx_donation_status", columnList = "status")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    // Nullable: a donor can give to the general fund with no campaign earmarked.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amountUsd;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "usd";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus status;

    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;

    // Stripe identifiers — whichever kicked off this donation.
    @Column(name = "stripe_checkout_session_id", unique = true)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "receipt_sent")
    @Builder.Default
    private Boolean receiptSent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = DonationStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum DonationStatus {
        PENDING, SUCCEEDED, FAILED, REFUNDED
    }
}

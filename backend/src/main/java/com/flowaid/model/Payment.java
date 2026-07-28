package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_recipient", columnList = "recipient_id"),
    @Index(name = "idx_payment_status", columnList = "status"),
    @Index(name = "idx_payment_campaign", columnList = "campaign_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Recipient recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "external_transfer_id", unique = true)
    private String externalTransferId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "initiated_at")
    private Instant initiatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Client- (or bulk-job-) supplied key used to guarantee a payment is only
    // ever created/processed once, even if the initiate call is retried by a
    // caller, a flaky network, or a duplicate bulk-disbursement request.
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    // --- Retry / backoff bookkeeping ---
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 5;

    // When the scheduled retry job is allowed to pick this payment up again.
    // Set using exponential backoff after each failed attempt.
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = PaymentStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum PaymentStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, RETRY_SCHEDULED, DEAD_LETTER, REVERSED
    }
}

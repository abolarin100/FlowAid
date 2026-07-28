package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable append-only record of every status transition a Payment goes
 * through. This is the "what happened, when, and why" trail that ops/compliance
 * need when reconciling a disbursement run — separate from Payment itself so
 * the transactional history can never be overwritten by a later update.
 */
@Entity
@Table(name = "payment_audit_logs", indexes = {
    @Index(name = "idx_audit_payment", columnList = "payment_id"),
    @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private Payment.PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private Payment.PaymentStatus newStatus;

    // e.g. "manual-retry", "scheduled-retry", "transfer-gateway", "dead-letter"
    @Column(name = "actor", length = 50)
    private String actor;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}

package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Stripe (and any future provider) can and will redeliver the same webhook
 * event more than once — on timeout, on retry, on duplicate delivery. This
 * table is the dedupe guard: before acting on an event, check whether its
 * id is already here; if so, it's a replay and gets ignored.
 */
@Entity
@Table(name = "processed_webhook_events", uniqueConstraints = {
    @UniqueConstraint(name = "uq_webhook_event_id", columnNames = {"provider", "event_id"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String provider; // e.g. "stripe"

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = Instant.now();
    }
}

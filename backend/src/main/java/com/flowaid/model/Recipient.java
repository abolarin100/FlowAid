package com.flowaid.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recipients", indexes = {
    @Index(name = "idx_recipient_phone", columnList = "phone_number"),
    @Index(name = "idx_recipient_country", columnList = "country_code"),
    @Index(name = "idx_recipient_status", columnList = "enrollment_status")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "preferred_payment_method", length = 50)
    private String preferredPaymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 20)
    private EnrollmentStatus enrollmentStatus;

    @Column(name = "vulnerability_score")
    private Integer vulnerabilityScore;

    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Payment> payments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (enrollmentStatus == null) enrollmentStatus = EnrollmentStatus.PENDING_VERIFICATION;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum EnrollmentStatus {
        PENDING_VERIFICATION, VERIFIED, ACTIVE, SUSPENDED, GRADUATED
    }
}

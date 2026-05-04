package com.flowaid.repository;

import com.flowaid.model.Payment;
import com.flowaid.model.Payment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<Payment> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Optional<Payment> findByExternalTransferId(String externalTransferId);

    List<Payment> findByCampaignIdAndStatus(UUID campaignId, PaymentStatus status);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.campaign.id = :campaignId
        AND p.status = :status
        AND p.createdAt >= :since
        """)
    List<Payment> findByCampaignAndStatusSince(
        @Param("campaignId") UUID campaignId,
        @Param("status") PaymentStatus status,
        @Param("since") Instant since
    );

    @Query("""
        SELECT SUM(p.amount) FROM Payment p
        WHERE p.campaign.id = :campaignId
        AND p.status = 'COMPLETED'
        """)
    Optional<BigDecimal> sumCompletedAmountByCampaign(@Param("campaignId") UUID campaignId);

    @Query("""
        SELECT p.status, COUNT(p), SUM(p.amount)
        FROM Payment p
        WHERE p.campaign.id = :campaignId
        GROUP BY p.status
        """)
    List<Object[]> getPaymentStatsByCampaign(@Param("campaignId") UUID campaignId);

    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.recipient.id = :recipientId
        AND p.status = 'COMPLETED'
        """)
    long countCompletedByRecipient(@Param("recipientId") UUID recipientId);

    // FIX Bug 1: Replaced PostgreSQL-only native DATE_TRUNC query with a JPQL
    // query that works on both H2 (dev) and PostgreSQL (production).
    // Groups by date by truncating to day using createdAt >= since filter.
    // For charting purposes, the DashboardService now uses JPQL aggregates instead.
    @Query("""
        SELECT p.createdAt, p.amount FROM Payment p
        WHERE p.createdAt >= :since
        ORDER BY p.createdAt
        """)
    List<Object[]> getDailyDisbursementStats(@Param("since") Instant since);

    // FIX Bug 4: Replaced Java-stream aggregation with proper DB-level queries
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    long countByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
}

package com.flowaid.repository;

import com.flowaid.model.Donation;
import com.flowaid.model.Donation.DonationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonationRepository extends JpaRepository<Donation, UUID> {

    Optional<Donation> findByStripeCheckoutSessionId(String sessionId);

    Optional<Donation> findByStripePaymentIntentId(String paymentIntentId);

    Page<Donation> findByDonorIdOrderByCreatedAtDesc(UUID donorId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(d.amountUsd), 0) FROM Donation d WHERE d.donor.id = :donorId AND d.status = 'SUCCEEDED'")
    BigDecimal sumSucceededByDonor(@Param("donorId") UUID donorId);

    @Query("SELECT COALESCE(SUM(d.amountUsd), 0) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCEEDED'")
    BigDecimal sumSucceededByCampaign(@Param("campaignId") UUID campaignId);

    long countByStatus(DonationStatus status);

    @Query("SELECT COALESCE(SUM(d.amountUsd), 0) FROM Donation d WHERE d.status = 'SUCCEEDED'")
    BigDecimal sumAllSucceeded();
}

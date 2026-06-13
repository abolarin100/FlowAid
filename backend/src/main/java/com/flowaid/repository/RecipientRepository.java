package com.flowaid.repository;

import com.flowaid.model.Recipient;
import com.flowaid.model.Recipient.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    Optional<Recipient> findByPhoneNumber(String phoneNumber);

    Page<Recipient> findByEnrollmentStatus(EnrollmentStatus status, Pageable pageable);

    Page<Recipient> findByCountryCode(String countryCode, Pageable pageable);

    @Query("""
            SELECT r FROM Recipient r
            WHERE r.countryCode = :countryCode
            AND r.enrollmentStatus = 'ACTIVE'
            AND r.id NOT IN (
                SELECT p.recipient.id FROM Payment p
                WHERE p.campaign.id = :campaignId
            )
            """)
    List<Recipient> findEligibleForCampaign(
            @Param("campaignId") UUID campaignId,
            @Param("countryCode") String countryCode);

    @Query("SELECT COUNT(r) FROM Recipient r WHERE r.enrollmentStatus = :status")
    long countByStatus(@Param("status") EnrollmentStatus status);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("""
            SELECT r FROM Recipient r
            WHERE r.countryCode = :countryCode
            AND r.region = :region
            AND r.enrollmentStatus = 'ACTIVE'
            AND r.id NOT IN (
                SELECT p.recipient.id FROM Payment p
                WHERE p.campaign.id = :campaignId
            )
            """)
    List<Recipient> findEligibleForCampaignInRegion(
            @Param("campaignId") UUID campaignId,
            @Param("countryCode") String countryCode,
            @Param("region") String region);

    @Query("""
            SELECT r FROM Recipient r
            WHERE r.countryCode = :countryCode
            AND r.enrollmentStatus = 'ACTIVE'
            AND r.id NOT IN (
                SELECT p.recipient.id FROM Payment p
                WHERE p.campaign.id = :campaignId
            )
            """)
    Page<Recipient> findEligibleForCampaignPaged(
            @Param("campaignId") UUID campaignId,
            @Param("countryCode") String countryCode,
            Pageable pageable);

    @Query("""
            SELECT r FROM Recipient r
            WHERE r.countryCode = :countryCode
            AND r.region = :region
            AND r.enrollmentStatus = 'ACTIVE'
            AND r.id NOT IN (
                SELECT p.recipient.id FROM Payment p
                WHERE p.campaign.id = :campaignId
            )
            """)
    Page<Recipient> findEligibleForCampaignInRegionPaged(
            @Param("campaignId") UUID campaignId,
            @Param("countryCode") String countryCode,
            @Param("region") String region,
            Pageable pageable);
}

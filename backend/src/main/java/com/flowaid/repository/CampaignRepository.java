package com.flowaid.repository;

import com.flowaid.model.Campaign;
import com.flowaid.model.Campaign.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findByStatus(CampaignStatus status);
    long countByStatus(CampaignStatus status);

    @Query("SELECT c FROM Campaign c WHERE c.targetCountry = :country AND c.status = 'ACTIVE'")
    List<Campaign> findActiveCampaignsByCountry(@Param("country") String countryCode);
}

package com.flowaid.service;

import com.flowaid.dto.CampaignDto;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Campaign.CampaignStatus;
import com.flowaid.repository.CampaignRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final DashboardService dashboardService;

    public CampaignService(CampaignRepository campaignRepository,
            @Lazy DashboardService dashboardService) {
        this.campaignRepository = campaignRepository;
        this.dashboardService = dashboardService;
    }

    @Transactional(readOnly = true)
    public List<CampaignDto.Response> listAll() {
        return campaignRepository.findAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampaignDto.Response getById(UUID id) {
        return campaignRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id));
    }

    @Transactional
    public CampaignDto.Response create(CampaignDto.CreateRequest request) {
        Campaign campaign = Campaign.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .status(CampaignStatus.DRAFT)
                .targetCountry(request.getTargetCountry())
                .targetRegion(request.getTargetRegion())
                .budgetUsd(request.getBudgetUsd())
                .disbursedUsd(BigDecimal.ZERO)
                .transferAmountUsd(request.getTransferAmountUsd())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign created: {} ({})", saved.getId(), saved.getName());
        dashboardService.evictCache();
        return toResponse(saved);
    }

    @Transactional
    public CampaignDto.Response updateStatus(UUID id, CampaignStatus newStatus) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", id));

        CampaignStatus old = campaign.getStatus();
        campaign.setStatus(newStatus);
        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign {} status: {} → {}", id, old, newStatus);
        dashboardService.evictCache();
        return toResponse(saved);
    }

    private CampaignDto.Response toResponse(Campaign c) {
        return CampaignDto.Response.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .type(c.getType())
                .status(c.getStatus())
                .targetCountry(c.getTargetCountry())
                .targetRegion(c.getTargetRegion())
                .budgetUsd(c.getBudgetUsd())
                .disbursedUsd(c.getDisbursedUsd())
                .transferAmountUsd(c.getTransferAmountUsd())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
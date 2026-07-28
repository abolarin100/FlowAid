package com.flowaid.service;

import com.flowaid.dto.CampaignDto;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Campaign.CampaignStatus;
import com.flowaid.model.Payment.PaymentStatus;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final PaymentRepository paymentRepository;
    private final DashboardService dashboardService;

    public CampaignService(CampaignRepository campaignRepository,
            PaymentRepository paymentRepository,
            @Lazy DashboardService dashboardService) {
        this.campaignRepository = campaignRepository;
        this.paymentRepository = paymentRepository;
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
                .slaTargetHours(request.getSlaTargetHours() != null ? request.getSlaTargetHours() : 120)
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

        // Start the SLA clock the first time a campaign goes live — this is
        // "time since crisis triggered" for CRISIS_RESPONSE/EMERGENCY_RELIEF campaigns.
        if (newStatus == CampaignStatus.ACTIVE && campaign.getTriggeredAt() == null) {
            campaign.setTriggeredAt(Instant.now());
        }

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign {} status: {} \u2192 {}", id, old, newStatus);
        dashboardService.evictCache();
        return toResponse(saved);
    }

    /**
     * Live "X of Y paid" progress plus an SLA countdown, for the rapid
     * disbursement dashboard. Recomputed fresh on every call (not cached)
     * since this is exactly the view ops staff are watching move in real time.
     */
    @Transactional(readOnly = true)
    public CampaignDto.Progress getProgress(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        List<Object[]> stats = paymentRepository.getPaymentStatsByCampaign(campaignId);
        long completed = 0, pendingOrProcessing = 0, retryScheduled = 0, deadLetter = 0, total = 0;
        for (Object[] row : stats) {
            PaymentStatus status = (PaymentStatus) row[0];
            long count = (long) row[1];
            total += count;
            switch (status) {
                case COMPLETED -> completed += count;
                case PENDING, PROCESSING -> pendingOrProcessing += count;
                case RETRY_SCHEDULED, FAILED -> retryScheduled += count;
                case DEAD_LETTER -> deadLetter += count;
                default -> { /* REVERSED etc. not counted toward progress */ }
            }
        }

        double percent = total == 0 ? 0.0 : Math.round((completed * 1000.0) / total) / 10.0;

        Long hoursElapsed = null, hoursRemaining = null;
        boolean breached = false;
        if (campaign.getTriggeredAt() != null && campaign.getSlaTargetHours() != null) {
            long elapsed = Duration.between(campaign.getTriggeredAt(), Instant.now()).toHours();
            hoursElapsed = elapsed;
            hoursRemaining = campaign.getSlaTargetHours() - elapsed;
            breached = hoursRemaining < 0 && completed < total;
        }

        return CampaignDto.Progress.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getName())
                .totalRecipients(total)
                .completedCount(completed)
                .pendingOrProcessingCount(pendingOrProcessing)
                .retryScheduledCount(retryScheduled)
                .deadLetterCount(deadLetter)
                .percentComplete(percent)
                .triggeredAt(campaign.getTriggeredAt())
                .slaTargetHours(campaign.getSlaTargetHours())
                .slaHoursElapsed(hoursElapsed)
                .slaHoursRemaining(hoursRemaining)
                .slaBreached(breached)
                .build();
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
                .triggeredAt(c.getTriggeredAt())
                .slaTargetHours(c.getSlaTargetHours())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}

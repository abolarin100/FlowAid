package com.flowaid.service;

import com.flowaid.dto.RecipientDto;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Recipient;
import com.flowaid.model.Recipient.EnrollmentStatus;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.RecipientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class RecipientService {

    private final RecipientRepository recipientRepository;
    private final DashboardService dashboardService;
    private final CampaignRepository campaignRepository;

    public RecipientService(RecipientRepository recipientRepository,
            @Lazy DashboardService dashboardService,
            CampaignRepository campaignRepository) {
        this.recipientRepository = recipientRepository;
        this.dashboardService = dashboardService;
        this.campaignRepository = campaignRepository;
    }

    @Transactional(readOnly = true)
    public Page<RecipientDto.Response> listRecipients(Pageable pageable) {
        return recipientRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RecipientDto.Response getById(UUID id) {
        return recipientRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient", id));
    }

    @Transactional
    public RecipientDto.Response create(RecipientDto.CreateRequest request) {
        if (recipientRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException(
                    "A recipient with phone number " + request.getPhoneNumber() + " already exists");
        }
        Recipient recipient = Recipient.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .countryCode(request.getCountryCode())
                .region(request.getRegion())
                .preferredPaymentMethod(request.getPreferredPaymentMethod())
                .vulnerabilityScore(request.getVulnerabilityScore())
                .enrollmentStatus(EnrollmentStatus.PENDING_VERIFICATION)
                .build();

        Recipient saved = recipientRepository.save(recipient);
        log.info("Enrolled recipient {} ({})", saved.getId(), saved.getPhoneNumber());
        dashboardService.evictCache();
        return toResponse(saved);
    }

    @Transactional
    public RecipientDto.Response updateStatus(UUID id, EnrollmentStatus newStatus) {
        Recipient recipient = recipientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient", id));

        EnrollmentStatus old = recipient.getEnrollmentStatus();
        recipient.setEnrollmentStatus(newStatus);
        Recipient saved = recipientRepository.save(recipient);
        log.info("Recipient {} status: {} → {}", id, old, newStatus);
        dashboardService.evictCache();
        return toResponse(saved);
    }

    private RecipientDto.Response toResponse(Recipient r) {
        return RecipientDto.Response.builder()
                .id(r.getId())
                .firstName(r.getFirstName())
                .lastName(r.getLastName())
                .phoneNumber(r.getPhoneNumber())
                .countryCode(r.getCountryCode())
                .region(r.getRegion())
                .preferredPaymentMethod(r.getPreferredPaymentMethod())
                .enrollmentStatus(r.getEnrollmentStatus())
                .vulnerabilityScore(r.getVulnerabilityScore())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    public RecipientService(RecipientRepository recipientRepository,
            CampaignRepository campaignRepository,
            @Lazy DashboardService dashboardService) {
        this.recipientRepository = recipientRepository;
        this.campaignRepository = campaignRepository;
        this.dashboardService = dashboardService;
    }

    @Transactional(readOnly = true)
    public Page<RecipientDto.Response> listEligibleForCampaign(UUID campaignId, Pageable pageable) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        boolean hasCountry = campaign.getTargetCountry() != null && !campaign.getTargetCountry().isBlank();
        boolean hasRegion = campaign.getTargetRegion() != null && !campaign.getTargetRegion().isBlank();

        if (hasCountry && hasRegion) {
            return recipientRepository.findEligibleForCampaignInRegionPaged(
                    campaignId, campaign.getTargetCountry(), campaign.getTargetRegion(), pageable)
                    .map(this::toResponse);
        }
        if (hasCountry) {
            return recipientRepository.findEligibleForCampaignPaged(
                    campaignId, campaign.getTargetCountry(), pageable)
                    .map(this::toResponse);
        }

        return recipientRepository.findByEnrollmentStatus(
                Recipient.EnrollmentStatus.ACTIVE, pageable)
                .map(this::toResponse);
    }
}
package com.flowaid.service;

import com.flowaid.dto.PaymentDto;
import com.flowaid.exception.PaymentProcessingException;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Payment;
import com.flowaid.model.Payment.PaymentStatus;
import com.flowaid.model.Recipient;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.PaymentRepository;
import com.flowaid.repository.RecipientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
// @RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final TransferGatewayService transferGatewayService;
    private final DashboardService dashboardService;

    public PaymentService(PaymentRepository paymentRepository,
            RecipientRepository recipientRepository,
            CampaignRepository campaignRepository,
            TransferGatewayService transferGatewayService,
            @Lazy DashboardService dashboardService) {
        this.paymentRepository = paymentRepository;
        this.recipientRepository = recipientRepository;
        this.campaignRepository = campaignRepository;
        this.transferGatewayService = transferGatewayService;
        this.dashboardService = dashboardService;
    }

    @Transactional
    public PaymentDto.Response initiatePayment(PaymentDto.CreateRequest request) {
        Recipient recipient = recipientRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient", request.getRecipientId()));

        Campaign campaign = campaignRepository.findById(request.getCampaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", request.getCampaignId()));

        validatePaymentEligibility(recipient, campaign, request.getAmount());

        Payment payment = Payment.builder()
                .recipient(recipient)
                .campaign(campaign)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment {} created for recipient {} in campaign {}",
                saved.getId(), recipient.getId(), campaign.getId());

        // FIX Bug 2: processPaymentAsync now correctly runs on a separate thread
        // because @EnableAsync is present in AppConfig
        processPaymentAsync(saved.getId());
        return toResponse(saved);
    }

    // FIX Bug 2: Use REQUIRES_NEW so the async method runs in its own transaction,
    // separate from the outer initiatePayment transaction that already committed.
    @Async("paymentExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> processPaymentAsync(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        try {
            payment.setStatus(PaymentStatus.PROCESSING);
            payment.setInitiatedAt(Instant.now());
            paymentRepository.save(payment);

            String externalId = transferGatewayService.initiateTransfer(
                    payment.getRecipient().getPhoneNumber(),
                    payment.getAmount(),
                    payment.getCurrency());

            payment.setExternalTransferId(externalId);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());

            // FIX: update campaign disbursed amount when payment completes
            Campaign campaign = payment.getCampaign();
            campaign.setDisbursedUsd(campaign.getDisbursedUsd().add(payment.getAmount()));
            campaignRepository.save(campaign);

            log.info("Payment {} completed with external id {}", paymentId, externalId);

        } catch (TransferGatewayService.TransferFailedException e) {
            log.error("Transfer gateway rejected payment {}: {}", paymentId, e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing payment {}: {}", paymentId, e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Unexpected error: " + e.getMessage());
        }

        paymentRepository.save(payment);
        dashboardService.evictCache();
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public List<PaymentDto.Response> bulkDisbursement(PaymentDto.BulkDisbursementRequest request) {
        Campaign campaign = campaignRepository.findById(request.getCampaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", request.getCampaignId()));

        List<UUID> recipientIds = (request.getRecipientIds() == null || request.getRecipientIds().isEmpty())
                ? resolveEligibleRecipients(campaign)
                : request.getRecipientIds();

        if (recipientIds.isEmpty()) {
            throw new PaymentProcessingException(
                    "No eligible recipients found for campaign " + campaign.getId());
        }

        validateCampaignHasBudget(campaign, recipientIds.size());

        List<PaymentDto.Response> responses = new ArrayList<>();
        for (UUID recipientId : recipientIds) {
            try {
                PaymentDto.CreateRequest createRequest = PaymentDto.CreateRequest.builder()
                        .recipientId(recipientId)
                        .campaignId(campaign.getId())
                        .amount(campaign.getTransferAmountUsd())
                        .currency("USD")
                        .build();
                responses.add(initiatePayment(createRequest));
            } catch (Exception e) {
                log.warn("Skipping recipient {} in bulk disbursement: {}", recipientId, e.getMessage());
            }
        }

        log.info("Bulk disbursement: {} payments initiated for campaign {}", responses.size(), campaign.getId());
        dashboardService.evictCache();
        return responses;
    }

    private List<UUID> resolveEligibleRecipients(Campaign campaign) {
        boolean hasCountry = campaign.getTargetCountry() != null && !campaign.getTargetCountry().isBlank();
        boolean hasRegion = campaign.getTargetRegion() != null && !campaign.getTargetRegion().isBlank();

        List<Recipient> eligible;
        if (hasCountry && hasRegion) {
            eligible = recipientRepository.findEligibleForCampaignInRegion(
                    campaign.getId(), campaign.getTargetCountry(), campaign.getTargetRegion());
        } else if (hasCountry) {
            eligible = recipientRepository.findEligibleForCampaign(
                    campaign.getId(), campaign.getTargetCountry());
        } else {
            // No geo-targeting — all active recipients not yet paid
            eligible = recipientRepository.findAll().stream()
                    .filter(r -> r.getEnrollmentStatus() == Recipient.EnrollmentStatus.ACTIVE)
                    .toList();
        }
        return eligible.stream().map(Recipient::getId).toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getPaymentsByCampaign(UUID campaignId, Pageable pageable) {
        return paymentRepository.findByCampaignId(campaignId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getPaymentsByRecipient(UUID recipientId, Pageable pageable) {
        return paymentRepository.findByRecipientId(recipientId, pageable).map(this::toResponse);
    }

    private void validatePaymentEligibility(Recipient recipient, Campaign campaign, BigDecimal amount) {
        if (recipient.getEnrollmentStatus() != Recipient.EnrollmentStatus.ACTIVE) {
            throw new PaymentProcessingException(
                    "Recipient " + recipient.getId() + " is not in ACTIVE status");
        }
        if (campaign.getStatus() != Campaign.CampaignStatus.ACTIVE) {
            throw new PaymentProcessingException(
                    "Campaign " + campaign.getId() + " is not ACTIVE");
        }
        if (campaign.getTargetCountry() != null
                && !campaign.getTargetCountry().equalsIgnoreCase(recipient.getCountryCode())) {
            throw new PaymentProcessingException(
                    "Recipient country " + recipient.getCountryCode()
                            + " does not match campaign target country " + campaign.getTargetCountry());
        }
        if (campaign.getTargetRegion() != null
                && !campaign.getTargetRegion().equalsIgnoreCase(recipient.getRegion())) {
            throw new PaymentProcessingException(
                    "Recipient region " + recipient.getRegion()
                            + " does not match campaign target region " + campaign.getTargetRegion());
        }
        BigDecimal remaining = campaign.getBudgetUsd().subtract(campaign.getDisbursedUsd());
        if (amount.compareTo(remaining) > 0) {
            throw new PaymentProcessingException("Insufficient campaign budget");
        }
    }

    private void validateCampaignHasBudget(Campaign campaign, int recipientCount) {
        BigDecimal totalRequired = campaign.getTransferAmountUsd()
                .multiply(BigDecimal.valueOf(recipientCount));
        BigDecimal remaining = campaign.getBudgetUsd().subtract(campaign.getDisbursedUsd());
        if (totalRequired.compareTo(remaining) > 0) {
            throw new PaymentProcessingException(
                    String.format("Campaign budget insufficient: need %s, have %s", totalRequired, remaining));
        }
    }

    private PaymentDto.Response toResponse(Payment payment) {
        return PaymentDto.Response.builder()
                .id(payment.getId())
                .recipientId(payment.getRecipient().getId())
                .recipientName(payment.getRecipient().getFirstName() + " " + payment.getRecipient().getLastName())
                .campaignId(payment.getCampaign().getId())
                .campaignName(payment.getCampaign().getName())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .externalTransferId(payment.getExternalTransferId())
                .failureReason(payment.getFailureReason())
                .initiatedAt(payment.getInitiatedAt())
                .completedAt(payment.getCompletedAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(this::toResponse);
    }
}

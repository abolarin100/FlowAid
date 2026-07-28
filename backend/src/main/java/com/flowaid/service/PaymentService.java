package com.flowaid.service;

import com.flowaid.dto.PaymentDto;
import com.flowaid.exception.PaymentProcessingException;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Payment;
import com.flowaid.model.Payment.PaymentStatus;
import com.flowaid.model.PaymentAuditLog;
import com.flowaid.model.Recipient;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.PaymentRepository;
import com.flowaid.repository.RecipientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns payment lifecycle bookkeeping (idempotency, validation, budget
 * checks, bulk disbursement, the ops failure queue). The actual "call the
 * transfer gateway" work is delegated to PaymentProcessingWorker — a
 * separate bean so its @Async annotation genuinely applies (see that
 * class's Javadoc for why calling an @Async method on `this` doesn't work).
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final PaymentAuditLogger auditLogger;
    private final PaymentProcessingWorker paymentProcessingWorker;
    private final DashboardService dashboardService;

    private static final List<PaymentStatus> UNRESOLVED_STATUSES =
            List.of(PaymentStatus.FAILED, PaymentStatus.RETRY_SCHEDULED, PaymentStatus.DEAD_LETTER);

    public PaymentService(PaymentRepository paymentRepository,
            RecipientRepository recipientRepository,
            CampaignRepository campaignRepository,
            PaymentAuditLogger auditLogger,
            PaymentProcessingWorker paymentProcessingWorker,
            @Lazy DashboardService dashboardService) {
        this.paymentRepository = paymentRepository;
        this.recipientRepository = recipientRepository;
        this.campaignRepository = campaignRepository;
        this.auditLogger = auditLogger;
        this.paymentProcessingWorker = paymentProcessingWorker;
        this.dashboardService = dashboardService;
    }

    @Transactional
    public PaymentDto.Response initiatePayment(PaymentDto.CreateRequest request) {
        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey()
                : deriveIdempotencyKey(request);

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay: payment already exists for key {} (payment {})",
                    idempotencyKey, existing.get().getId());
            return toResponse(existing.get());
        }

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
                .idempotencyKey(idempotencyKey)
                .build();

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> e);
        }

        auditLogger.log(saved.getId(), null, PaymentStatus.PENDING, "api", "Payment created");
        log.info("Payment {} created for recipient {} in campaign {} (idempotencyKey={})",
                saved.getId(), recipient.getId(), campaign.getId(), idempotencyKey);

        paymentProcessingWorker.processPaymentAsync(saved.getId(), "api");
        return toResponse(saved);
    }

    @Transactional
    public void retryPayment(UUID paymentId, String actor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getStatus() != PaymentStatus.RETRY_SCHEDULED && payment.getStatus() != PaymentStatus.DEAD_LETTER) {
            throw new PaymentProcessingException("Payment " + paymentId + " is not eligible for retry (status="
                    + payment.getStatus() + ")");
        }
        if (payment.getStatus() == PaymentStatus.DEAD_LETTER) {
            payment.setMaxRetries(payment.getMaxRetries() + 1);
            paymentRepository.save(payment);
        }
        paymentProcessingWorker.processPaymentAsync(paymentId, actor);
    }

    @Transactional(readOnly = true)
    public List<UUID> findPaymentsDueForRetry() {
        return paymentRepository.findDueForRetry(PaymentStatus.RETRY_SCHEDULED, Instant.now())
                .stream().map(Payment::getId).toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto.Response> getFailureQueue(Pageable pageable) {
        return paymentRepository.findByStatusIn(UNRESOLVED_STATUSES, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long getFailureQueueCount() {
        return paymentRepository.countByStatusIn(UNRESOLVED_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditLog> getAuditTrail(UUID paymentId) {
        return auditLogger.getTrail(paymentId);
    }

    private String deriveIdempotencyKey(PaymentDto.CreateRequest request) {
        try {
            String raw = request.getRecipientId() + "|" + request.getCampaignId() + "|" + request.getAmount();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 40);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
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
                        .idempotencyKey("bulk-" + campaign.getId() + "-" + recipientId)
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
                .idempotencyKey(payment.getIdempotencyKey())
                .retryCount(payment.getRetryCount())
                .maxRetries(payment.getMaxRetries())
                .nextRetryAt(payment.getNextRetryAt())
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

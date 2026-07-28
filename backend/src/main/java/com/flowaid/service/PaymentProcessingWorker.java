package com.flowaid.service;

import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Payment;
import com.flowaid.model.Payment.PaymentStatus;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the actual "call the transfer gateway and record the outcome" work.
 * Deliberately a SEPARATE bean from PaymentService: Spring's @Async only
 * takes effect on calls that go through the bean's proxy, which means a
 * method can't call its own @Async sibling via `this.foo()` — that
 * self-invocation runs synchronously and silently ignores the annotation.
 * By putting this in its own service, PaymentService's calls into it are
 * genuine cross-bean calls, so @Async (and the dedicated paymentExecutor
 * thread pool) actually applies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingWorker {

    private final PaymentRepository paymentRepository;
    private final CampaignRepository campaignRepository;
    private final TransferGatewayService transferGatewayService;
    private final PaymentAuditLogger auditLogger;
    private final DashboardService dashboardService;

    @Async("paymentExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> processPaymentAsync(UUID paymentId, String actor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        PaymentStatus previous = payment.getStatus();
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setInitiatedAt(payment.getInitiatedAt() == null ? Instant.now() : payment.getInitiatedAt());
        payment.setLastAttemptedAt(Instant.now());
        paymentRepository.save(payment);
        auditLogger.log(paymentId, previous, PaymentStatus.PROCESSING, actor,
                "Attempt #" + (payment.getRetryCount() + 1));

        try {
            String externalId = transferGatewayService.initiateTransfer(
                    payment.getRecipient().getPhoneNumber(),
                    payment.getRecipient().getCountryCode(),
                    payment.getAmount(),
                    payment.getCurrency());

            payment.setExternalTransferId(externalId);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
            payment.setFailureReason(null);
            payment.setNextRetryAt(null);

            Campaign campaign = payment.getCampaign();
            campaign.setDisbursedUsd(campaign.getDisbursedUsd().add(payment.getAmount()));
            campaignRepository.save(campaign);

            paymentRepository.save(payment);
            auditLogger.log(paymentId, PaymentStatus.PROCESSING, PaymentStatus.COMPLETED, actor,
                    "Completed via external id " + externalId);
            log.info("Payment {} completed with external id {}", paymentId, externalId);

        } catch (Exception e) {
            handleFailure(payment, actor, e);
        }

        dashboardService.evictCache();
        return CompletableFuture.completedFuture(null);
    }

    private void handleFailure(Payment payment, String actor, Exception e) {
        String reason = e instanceof TransferGatewayService.TransferFailedException
                ? e.getMessage()
                : "Unexpected error: " + e.getMessage();

        payment.setFailureReason(reason);
        payment.setRetryCount(payment.getRetryCount() + 1);

        if (payment.getRetryCount() >= payment.getMaxRetries()) {
            payment.setStatus(PaymentStatus.DEAD_LETTER);
            payment.setNextRetryAt(null);
            paymentRepository.save(payment);
            auditLogger.log(payment.getId(), PaymentStatus.PROCESSING, PaymentStatus.DEAD_LETTER, actor,
                    "Exhausted " + payment.getMaxRetries() + " attempts: " + reason);
            log.error("Payment {} moved to DEAD_LETTER after {} attempts: {}",
                    payment.getId(), payment.getRetryCount(), reason);
        } else {
            payment.setStatus(PaymentStatus.RETRY_SCHEDULED);
            Instant nextRetry = Instant.now().plus(backoff(payment.getRetryCount()));
            payment.setNextRetryAt(nextRetry);
            paymentRepository.save(payment);
            auditLogger.log(payment.getId(), PaymentStatus.PROCESSING, PaymentStatus.RETRY_SCHEDULED, actor,
                    "Attempt " + payment.getRetryCount() + " failed (" + reason + "); next retry at " + nextRetry);
            log.warn("Payment {} failed (attempt {}), retry scheduled at {}: {}",
                    payment.getId(), payment.getRetryCount(), nextRetry, reason);
        }
    }

    // Exponential backoff: 1m, 2m, 4m, 8m, 16m ... capped at 1 hour.
    private Duration backoff(int retryCount) {
        long minutes = Math.min(60L, (long) Math.pow(2, Math.max(0, retryCount - 1)));
        return Duration.ofMinutes(minutes);
    }
}

package com.flowaid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Polls for FAILED payments whose backoff window has elapsed and re-attempts
 * them automatically, without any human needing to click "retry". Payments
 * that exhaust their retry budget are moved to DEAD_LETTER by PaymentService
 * and surface on the ops failure queue for manual intervention instead of
 * being retried forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final PaymentService paymentService;

    // Every 30s, look for payments whose nextRetryAt has passed.
    @Scheduled(fixedDelayString = "${flowaid.retry.poll-interval-ms:30000}")
    public void retryDuePayments() {
        List<UUID> due = paymentService.findPaymentsDueForRetry();
        if (due.isEmpty()) {
            return;
        }
        log.info("Retry scheduler: {} payment(s) due for automatic retry", due.size());
        for (UUID paymentId : due) {
            try {
                paymentService.retryPayment(paymentId, "scheduled-retry");
            } catch (Exception e) {
                log.warn("Retry scheduler failed to re-queue payment {}: {}", paymentId, e.getMessage());
            }
        }
    }
}

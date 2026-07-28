package com.flowaid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentProcessingWorker paymentProcessingWorker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCreated(PaymentCreatedEvent event) {
        log.debug("Transaction committed for payment {}, dispatching to worker", event.paymentId());
        paymentProcessingWorker.processPaymentAsync(event.paymentId(), event.actor());
    }
}
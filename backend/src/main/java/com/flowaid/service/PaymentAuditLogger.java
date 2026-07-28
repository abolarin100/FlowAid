package com.flowaid.service;

import com.flowaid.model.Payment.PaymentStatus;
import com.flowaid.model.PaymentAuditLog;
import com.flowaid.repository.PaymentAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentAuditLogger {

    private final PaymentAuditLogRepository auditLogRepository;

    public void log(UUID paymentId, PaymentStatus previous, PaymentStatus next, String actor, String detail) {
        auditLogRepository.save(PaymentAuditLog.builder()
                .paymentId(paymentId)
                .previousStatus(previous)
                .newStatus(next)
                .actor(actor)
                .detail(detail)
                .build());
    }

    public List<PaymentAuditLog> getTrail(UUID paymentId) {
        return auditLogRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }
}

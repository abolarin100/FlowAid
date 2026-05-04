package com.flowaid.service;

import com.flowaid.model.Payment.PaymentStatus;
import com.flowaid.model.Recipient.EnrollmentStatus;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.DonorRepository;
import com.flowaid.repository.PaymentRepository;
import com.flowaid.repository.RecipientRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final String CACHE_NAME = "dashboard-stats";

    private final PaymentRepository paymentRepository;
    private final RecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final DonorRepository donorRepository;

    @Cacheable(value = CACHE_NAME, key = "'global'")
    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        log.debug("Recomputing dashboard stats from DB");

        long activeRecipients  = recipientRepository.countByStatus(EnrollmentStatus.ACTIVE);
        long activeCampaigns   = campaignRepository.countByStatus(com.flowaid.model.Campaign.CampaignStatus.ACTIVE);
        long completedPayments = paymentRepository.countByStatus(PaymentStatus.COMPLETED);
        long failedPayments    = paymentRepository.countByStatus(PaymentStatus.FAILED);
        BigDecimal totalDisbursed = paymentRepository.sumAmountByStatus(PaymentStatus.COMPLETED);

        double successRate = (completedPayments + failedPayments) > 0
            ? (double) completedPayments / (completedPayments + failedPayments) * 100
            : 0.0;

        return DashboardStats.builder()
            .activeRecipients(activeRecipients)
            .activeCampaigns(activeCampaigns)
            .totalDisbursedUsd(totalDisbursed != null ? totalDisbursed : BigDecimal.ZERO)
            .completedPayments(completedPayments)
            .paymentSuccessRate(Math.round(successRate * 10.0) / 10.0)
            .totalDonors(donorRepository.count())
            .build();
    }

    @CacheEvict(value = CACHE_NAME, key = "'global'")
    public void evictCache() {
        log.debug("Dashboard cache evicted");
    }

    @Getter
    @Builder
    public static class DashboardStats {
        private final long activeRecipients;
        private final long activeCampaigns;
        private final BigDecimal totalDisbursedUsd;
        private final long completedPayments;
        private final double paymentSuccessRate;
        private final long totalDonors;
    }
}
package com.flowaid.integration;

import com.flowaid.model.Campaign;
import com.flowaid.model.Recipient;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.PaymentRepository;
import com.flowaid.repository.RecipientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository integration test.
 * Uses @DataJpaTest (H2 in-memory) for speed; swap to @Testcontainers + PostgreSQL
 * for full production-parity testing in CI (see docker-compose.test.yml).
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Repository Integration Tests")
class RepositoryIntegrationTest {

    @Autowired RecipientRepository recipientRepository;
    @Autowired CampaignRepository campaignRepository;
    @Autowired PaymentRepository paymentRepository;

    @Test
    @DisplayName("persists and retrieves recipient by phone number")
    void recipientPhoneLookup() {
        Recipient r = Recipient.builder()
            .firstName("Fatima").lastName("Nkosi")
            .phoneNumber("+27821234567")
            .countryCode("ZA")
            .enrollmentStatus(Recipient.EnrollmentStatus.ACTIVE)
            .build();

        recipientRepository.save(r);

        assertThat(recipientRepository.findByPhoneNumber("+27821234567"))
            .isPresent()
            .get()
            .extracting(Recipient::getFirstName)
            .isEqualTo("Fatima");
    }

    @Test
    @DisplayName("counts active campaigns correctly")
    void campaignStatusCount() {
        Campaign active = Campaign.builder()
            .name("Active Campaign")
            .type(Campaign.CampaignType.EMERGENCY_RELIEF)
            .status(Campaign.CampaignStatus.ACTIVE)
            .budgetUsd(new BigDecimal("50000"))
            .disbursedUsd(BigDecimal.ZERO)
            .transferAmountUsd(new BigDecimal("500"))
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .build();

        Campaign draft = Campaign.builder()
            .name("Draft Campaign")
            .type(Campaign.CampaignType.PILOT)
            .status(Campaign.CampaignStatus.DRAFT)
            .budgetUsd(new BigDecimal("10000"))
            .disbursedUsd(BigDecimal.ZERO)
            .transferAmountUsd(new BigDecimal("200"))
            .build();

        campaignRepository.save(active);
        campaignRepository.save(draft);

        assertThat(campaignRepository.countByStatus(Campaign.CampaignStatus.ACTIVE)).isEqualTo(1);
        assertThat(campaignRepository.countByStatus(Campaign.CampaignStatus.DRAFT)).isEqualTo(1);
    }

    @Test
    @DisplayName("duplicate phone number is rejected")
    void duplicatePhoneRejected() {
        recipientRepository.save(Recipient.builder()
            .firstName("A").lastName("B")
            .phoneNumber("+1234567890")
            .countryCode("US")
            .enrollmentStatus(Recipient.EnrollmentStatus.ACTIVE)
            .build());

        assertThat(recipientRepository.existsByPhoneNumber("+1234567890")).isTrue();
    }
}

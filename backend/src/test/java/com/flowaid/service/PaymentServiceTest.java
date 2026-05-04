package com.flowaid.service;

import com.flowaid.dto.PaymentDto;
import com.flowaid.exception.PaymentProcessingException;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Campaign;
import com.flowaid.model.Campaign.CampaignStatus;
import com.flowaid.model.Campaign.CampaignType;
import com.flowaid.model.Payment;
import com.flowaid.model.Recipient;
import com.flowaid.model.Recipient.EnrollmentStatus;
import com.flowaid.repository.CampaignRepository;
import com.flowaid.repository.PaymentRepository;
import com.flowaid.repository.RecipientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock RecipientRepository recipientRepository;
    @Mock CampaignRepository campaignRepository;
    @Mock TransferGatewayService transferGatewayService;

    @InjectMocks PaymentService paymentService;

    private Recipient activeRecipient;
    private Campaign activeCampaign;
    private PaymentDto.CreateRequest validRequest;

    @BeforeEach
    void setUp() {
        activeRecipient = Recipient.builder()
            .id(UUID.randomUUID())
            .firstName("Amara").lastName("Diallo")
            .phoneNumber("+2348012345678")
            .countryCode("NG")
            .enrollmentStatus(EnrollmentStatus.ACTIVE)
            .build();

        activeCampaign = Campaign.builder()
            .id(UUID.randomUUID())
            .name("Nigeria Emergency Relief 2024")
            .type(CampaignType.EMERGENCY_RELIEF)
            .status(CampaignStatus.ACTIVE)
            .budgetUsd(new BigDecimal("100000.00"))
            .disbursedUsd(new BigDecimal("10000.00"))
            .transferAmountUsd(new BigDecimal("500.00"))
            .targetCountry("NG")
            .build();

        validRequest = PaymentDto.CreateRequest.builder()
            .recipientId(activeRecipient.getId())
            .campaignId(activeCampaign.getId())
            .amount(new BigDecimal("500.00"))
            .currency("USD")
            .build();
    }

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePayment {

        @Test
        @DisplayName("creates payment successfully for eligible recipient")
        void successfulPayment() {
            when(recipientRepository.findById(activeRecipient.getId()))
                .thenReturn(Optional.of(activeRecipient));
            when(campaignRepository.findById(activeCampaign.getId()))
                .thenReturn(Optional.of(activeCampaign));
            when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            PaymentDto.Response response = paymentService.initiatePayment(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.getAmount()).isEqualByComparingTo("500.00");
            assertThat(response.getCurrency()).isEqualTo("USD");
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when recipient not found")
        void recipientNotFound() {
            when(recipientRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Recipient");
        }

        @Test
        @DisplayName("throws PaymentProcessingException for suspended recipient")
        void suspendedRecipient() {
            activeRecipient.setEnrollmentStatus(EnrollmentStatus.SUSPENDED);
            when(recipientRepository.findById(any())).thenReturn(Optional.of(activeRecipient));
            when(campaignRepository.findById(any())).thenReturn(Optional.of(activeCampaign));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("throws PaymentProcessingException when campaign budget exceeded")
        void insufficientBudget() {
            validRequest.setAmount(new BigDecimal("999999.00"));
            when(recipientRepository.findById(any())).thenReturn(Optional.of(activeRecipient));
            when(campaignRepository.findById(any())).thenReturn(Optional.of(activeCampaign));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("budget");
        }

        @Test
        @DisplayName("throws PaymentProcessingException for inactive campaign")
        void inactiveCampaign() {
            activeCampaign.setStatus(CampaignStatus.PAUSED);
            when(recipientRepository.findById(any())).thenReturn(Optional.of(activeRecipient));
            when(campaignRepository.findById(any())).thenReturn(Optional.of(activeCampaign));

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("ACTIVE");
        }
    }
}
